/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.contract.pocm.manager.deposit;

import io.nuls.contract.pocm.manager.ConsensusManager;
import io.nuls.contract.pocm.model.AgentInfo;
import io.nuls.contract.pocm.model.ConsensusDepositInfo;
import io.nuls.contract.sdk.Utils;

import java.math.BigInteger;
import java.util.*;

import static io.nuls.contract.pocm.manager.ConsensusManager.MAX_TOTAL_DEPOSIT;
import static io.nuls.contract.pocm.util.PocmUtil.toNuls;
import static io.nuls.contract.sdk.Utils.require;

/**
 * @author: PierreLuo
 * @date: 2019-06-25
 */
public class DepositOthersManager {
    /**
     * 在节点不能运行时，委托到其他节点，即抵押的金额不足20W之前，委托到其他节点
     */
    private Map<String, AgentInfo> otherAgents;

    // 委托信息列表
    private LinkedList<ConsensusDepositInfo> depositList = new LinkedList<ConsensusDepositInfo>();
    // 委托其他节点的锁定金额
    private BigInteger depositLockedAmount = BigInteger.ZERO;

    private BigInteger MIN_JOIN_DEPOSIT;

    public DepositOthersManager() {
        otherAgents = new HashMap<String, AgentInfo>();
    }

    public int otherAgentsSize() {
        return otherAgents.size();
    }

    public void repairDeposit(BigInteger value) {
        this.depositLockedAmount = this.depositLockedAmount.add(value);
    }

    public Set<String> getAgents() {
        int size = otherAgents.size();
        if(size == 0) {
            return null;
        }
        return otherAgents.keySet();
    }

    public BigInteger otherDepositLockedAmount() {
        return depositLockedAmount;
    }

    public String[] addOtherAgent(String agentHash) {
        require(!otherAgents.containsKey(agentHash), "Duplicate node hash");
        otherAgents.put(agentHash, new AgentInfo());
        Object agentInfo = Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
        require(agentInfo != null, "Invalid node hash");
        String[] agent = (String[]) agentInfo;
        // 注销高度，-1代表正常
        require("-1".equals(agent[8]), "Invalid node");
        return agent;
    }

    private boolean isEnableAgentNode(String agentHash) {
        Object agentInfo = Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
        boolean isEnabled;
        if (agentInfo == null ){
            isEnabled = false;
        } else {
            String[] agent = (String[])agentInfo;
            // 注销高度，-1代表正常
            isEnabled = "-1".equals(agent[8]);
        }
        return isEnabled;
    }

    public void removeAgent(String agentHash, ConsensusManager consensusManager) {
        BigInteger withdrawAmount = BigInteger.ZERO;
        require(otherAgents.containsKey(agentHash), "Node not exist");
        boolean isEnabled = isEnableAgentNode(agentHash);
        //退出节点委托
        Iterator<ConsensusDepositInfo> iterator = depositList.iterator();
        while (iterator.hasNext()){
            ConsensusDepositInfo depositInfo = iterator.next();
            if(agentHash.equals(depositInfo.getAgentHash())){
                if(isEnabled){
                    this.withdrawOne(depositInfo, consensusManager);
                }else{
                    depositLockedAmount = depositLockedAmount.subtract(depositInfo.getDeposit());
                }
                iterator.remove();
                withdrawAmount = withdrawAmount.add(depositInfo.getDeposit());
            }
        }
        if (!isEnabled) {
            consensusManager.addAvailableAmount(withdrawAmount);
        }
        //清除委托节点
        otherAgents.remove(agentHash);
    }

    public BigInteger deposit(BigInteger availableAmount, ConsensusManager consensusManager) {
        int size = otherAgents.size();
        if(size == 0) {
            // 没有其他节点的共识信息，跳过此流程
            return BigInteger.ZERO;
        }
        BigInteger actualDeposit = this.depositCheck(availableAmount, consensusManager);
        return actualDeposit;
    }

    private BigInteger depositCheck(BigInteger availableAmount, ConsensusManager consensusManager) {
        BigInteger actualDeposit = BigInteger.ZERO;
        BigInteger[] amounts = new BigInteger[]{availableAmount, actualDeposit};
        this.depositCheckRecursion(amounts, consensusManager, new HashSet<String>());
        return amounts[1];
    }

    // avoid ConcurrentModificationException of 'otherAgents'
    private void depositCheckRecursion(BigInteger[] amounts, ConsensusManager consensusManager, Set<String> exclude) {
        BigInteger availableAmount = amounts[0];
        BigInteger actualDeposit = amounts[1];
        String[] agentInfo;
        boolean isEnabled;
        Set<Map.Entry<String, AgentInfo>> entries = otherAgents.entrySet();
        for(Map.Entry<String, AgentInfo> entry : entries) {
            String agentHash = entry.getKey();
            if (exclude.contains(agentHash)) continue;
            AgentInfo agent = entry.getValue();
            agentInfo = (String[]) Utils.invokeExternalCmd("cs_getContractAgentInfo", new String[]{agentHash});
            // 用户质押时，若遇到节点注销，则移除节点
            if (agentInfo == null ){
                isEnabled = false;
            } else {
                // 注销高度，-1代表正常
                isEnabled = "-1".equals(agentInfo[8]);
            }
            if (!isEnabled) {
                consensusManager.removeAgentInner(agentHash);
                this.depositCheckRecursion(amounts, consensusManager, exclude);
                break;
            }
            exclude.add(agentHash);
            // 合约节点已委托金额
            BigInteger totalDeposit = this.moreDeposits(agent, new BigInteger(agentInfo[4]));
            BigInteger currentAvailable = MAX_TOTAL_DEPOSIT.subtract(totalDeposit);
            if(currentAvailable.compareTo(availableAmount) >= 0) {
                if(availableAmount.compareTo(MIN_JOIN_DEPOSIT) >= 0) {
                    this.depositOne(agentHash, availableAmount, agent, consensusManager);
                    actualDeposit = actualDeposit.add(availableAmount);
                }
                break;
            } else if(currentAvailable.compareTo(MIN_JOIN_DEPOSIT) >= 0){
                this.depositOne(agentHash, currentAvailable, agent, consensusManager);
                actualDeposit = actualDeposit.add(currentAvailable);
                availableAmount = availableAmount.subtract(currentAvailable);
            }
        }
        amounts[0] = availableAmount;
        amounts[1] = actualDeposit;
    }

    private BigInteger moreDeposits(AgentInfo agent, BigInteger totalDepositFromCmd) {
        BigInteger agentDeposits = agent.getAgentDeposits();
        BigInteger total;
        if(agentDeposits.compareTo(totalDepositFromCmd) > 0) {
            total = agentDeposits;
        } else {
            total = totalDepositFromCmd;
        }
        return total;
    }

    /**
     * @param expectWithdrawAmount 期望退出的金额
     * @param consensusManager
     * @return actualWithdrawAmount 实际退出的金额(始终大于或等于期望值)
     */
    public void withdrawInner(BigInteger expectWithdrawAmount, ConsensusManager consensusManager) {
        // 退出所有委托
        if(expectWithdrawAmount.compareTo(depositLockedAmount) >= 0) {
            // 从共识中退出所有金额，则无需返回实际退出金额，内部已返回退出金额
            this.withdrawWhole(consensusManager, new HashSet<String>());
            depositLockedAmount = BigInteger.ZERO;
            depositList.clear();
        } else {
            BigInteger withdrawAmount;
            while (expectWithdrawAmount.compareTo(BigInteger.ZERO) > 0){
                withdrawAmount = this.withdrawLoop(expectWithdrawAmount, consensusManager);
                if (withdrawAmount.compareTo(BigInteger.ZERO) == 0) break;
                if(withdrawAmount.compareTo(expectWithdrawAmount) >= 0){
                    expectWithdrawAmount = BigInteger.ZERO;
                }else{
                    expectWithdrawAmount = expectWithdrawAmount.subtract(withdrawAmount);
                }
            }
        }
    }

    public BigInteger consensusEmergencyWithdraw(String joinAgentHash, ConsensusManager consensusManager) {
        BigInteger withdrawAmount = BigInteger.ZERO;
        //退出节点委托
        Iterator<ConsensusDepositInfo> iterator = depositList.iterator();
        while (iterator.hasNext()){
            ConsensusDepositInfo depositInfo = iterator.next();
            if(joinAgentHash.equals(depositInfo.getHash())){
                this.withdrawOne(depositInfo, consensusManager);
                iterator.remove();
                withdrawAmount = withdrawAmount.add(depositInfo.getDeposit());
                break;
            }
        }
        return withdrawAmount;
    }

    // avoid ConcurrentModificationException of 'depositList'
    private void withdrawWhole(ConsensusManager consensusManager, Set<String> exclude) {
        for(ConsensusDepositInfo info : depositList) {
            if (exclude.contains(info.getHash())) continue;
            boolean agentRemoved = (this.withdrawOneWithCheck(info, consensusManager).compareTo(BigInteger.ZERO) < 0);
            if (agentRemoved) {
                withdrawWhole(consensusManager, exclude);
                break;
            } else {
                exclude.add(info.getHash());
            }
        }
    }

    private String depositOne(String agentHash, BigInteger depositNa, AgentInfo agent, ConsensusManager consensusManager) {
        String[] args = new String[]{agentHash, depositNa.toString()};
        String txHash = (String) Utils.invokeExternalCmd("cs_contractDeposit", args);
        this.orderlyAdditionToDepositList(new ConsensusDepositInfo(agentHash, txHash, depositNa));
        depositLockedAmount = depositLockedAmount.add(depositNa);
        agent.add(depositNa);
        consensusManager.subAvailableAmount(depositNa);
        return txHash;
    }

    private BigInteger withdrawOneWithCheck(ConsensusDepositInfo info, ConsensusManager consensusManager) {
        String agentHash = info.getAgentHash();
        boolean isEnabled = isEnableAgentNode(agentHash);
        if (!isEnabled) {
            AgentInfo agent = otherAgents.get(agentHash);
            BigInteger agentDeposits = agent.getAgentDeposits();
            consensusManager.removeAgentInner(agentHash);
            return agentDeposits.negate();
        }
        this.withdrawOne(info, consensusManager);
        return info.getDeposit();
    }

    private String withdrawOne(ConsensusDepositInfo info, ConsensusManager consensusManager) {
        String agentHash = info.getAgentHash();
        String joinAgentHash = info.getHash();
        String[] args = new String[]{joinAgentHash};
        String txHash = (String) Utils.invokeExternalCmd("cs_contractWithdraw", args);
        BigInteger deposit = info.getDeposit();
        depositLockedAmount = depositLockedAmount.subtract(deposit);
        AgentInfo agent = otherAgents.get(agentHash);
        agent.subtract(deposit);
        consensusManager.addAvailableAmount(deposit);
        return txHash;
    }

    private BigInteger withdrawLoop(BigInteger expectWithdrawAmount, ConsensusManager consensusManager){
        if (depositList.size() == 0) {
            return BigInteger.ZERO;
        }
        ConsensusDepositInfo maxDeposit = depositList.getLast();
        BigInteger realWithdrawAmount = BigInteger.ZERO;
        //当退出金额大于最大的委托则退出最大委托；否则从小到大遍历找到大于退出金额的第一条委托记录
        if(expectWithdrawAmount.compareTo(maxDeposit.getDeposit()) >= 0){
            realWithdrawAmount = this.withdrawOneWithCheck(maxDeposit, consensusManager);
            boolean depositRemoved = (realWithdrawAmount.compareTo(BigInteger.ZERO) < 0);
            if (!depositRemoved) {
                depositList.removeLast();
            }
        }else{
            // 找出最小的一笔退出抵押的金额（使闲置金额最小）
            for(Iterator<ConsensusDepositInfo> iterator = depositList.iterator(); iterator.hasNext();){
                ConsensusDepositInfo info = iterator.next();
                if(info.getDeposit().compareTo(expectWithdrawAmount) >= 0) {
                    realWithdrawAmount = this.withdrawOneWithCheck(info, consensusManager);
                    boolean depositRemoved = (realWithdrawAmount.compareTo(BigInteger.ZERO) < 0);
                    // avoid ConcurrentModificationException of 'depositList'
                    if (!depositRemoved) {
                        iterator.remove();
                    }
                    break;
                }
            }
        }
        return realWithdrawAmount.abs();
    }

    /**
     * 按金额升序
     */
    private void orderlyAdditionToDepositList(ConsensusDepositInfo info) {
        BigInteger deposit = info.getDeposit();
        int size = depositList.size();
        if (size == 0) {
            depositList.add(info);
            return;
        }
        BigInteger compare;
        int result;
        int last = size - 1;
        for (int i = 0; i < size; i++) {
            compare = depositList.get(i).getDeposit();
            result = compare.compareTo(deposit);
            if (result < 0) {
                if (i == last) {
                    depositList.addLast(info);
                    break;
                }
                continue;
            } else if (result == 0) {
                depositList.add(i + 1, info);
                break;
            } else {
                depositList.add(i, info);
                break;
            }
        }
    }

    public void modifyMinJoinDeposit(BigInteger value) {
        MIN_JOIN_DEPOSIT = value;
    }

    public BigInteger getMinJoinDeposit() {
        return MIN_JOIN_DEPOSIT;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"otherAgents\":");
        sb.append('[');
        Set<Map.Entry<String, AgentInfo>> entries = otherAgents.entrySet();
        for(Map.Entry<String, AgentInfo> entry : entries) {
            String hash = entry.getKey();
            AgentInfo info = entry.getValue();

            sb.append("{\"hash\":\"").append(hash).append('\"').append(",");
            sb.append("\"agentDeposits\":").append('\"').append(info.getAgentDeposits().toString()).append("\"},");
        }
        if (otherAgents.size() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(']');

        sb.append(",\"depositList\":");
        sb.append('[');
        for (ConsensusDepositInfo info : depositList) {
            sb.append(info.toString()).append(',');
        }
        if (depositList.size() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(']');

        sb.append(",\"depositLockedAmount\":")
                .append('\"').append(toNuls(depositLockedAmount).toPlainString()).append('\"');
        sb.append('}');
        return sb.toString();
    }
}
