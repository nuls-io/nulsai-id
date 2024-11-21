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
package io.nuls.contract.pocm.manager;

import io.nuls.contract.pocm.NulsDomainStaking;
import io.nuls.contract.pocm.event.PocmRemoveAgentEvent;
import io.nuls.contract.pocm.manager.deposit.DepositOthersManager;
import io.nuls.contract.pocm.model.ConsensusAgentDepositInfo;
import io.nuls.contract.pocm.model.ConsensusAwardInfo;
import io.nuls.contract.pocm.model.UserInfo;
import io.nuls.contract.pocm.util.PocmUtil;
import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Msg;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import static io.nuls.contract.pocm.util.PocmUtil.MININUM_TRANSFER_AMOUNT;
import static io.nuls.contract.pocm.util.PocmUtil.toNuls;
import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;

/**
 * @author: PierreLuo
 * @date: 2019-05-14
 */
public class ConsensusManager {
    // 2K
    private BigInteger MIN_JOIN_DEPOSIT = BigInteger.valueOf(200000000000L);
    // 50W
    public static final BigInteger MAX_TOTAL_DEPOSIT = BigInteger.valueOf(50000000000000L);

    public static final String ACTIVE_AGENT = "1";
    // 可用金额
    private BigInteger availableAmount = BigInteger.ZERO;
    // 共识奖励金额信息
    private ConsensusAwardInfo awardInfo;
    /**
     * 开启委托到其他节点的功能
     */
    private boolean enableDepositOthers = false;
    private DepositOthersManager depositOthersManager;

    private Map<String, UserInfo> userInfo;
    private Map<String, ConsensusAgentDepositInfo> agentDeposits;
    private NulsDomainStaking pocmContract;
    private PocmInfo pi;
    private BigInteger pendingTreasury = BigInteger.ZERO;

    public ConsensusManager(Map<String, UserInfo> userInfo,
                            Map<String, ConsensusAgentDepositInfo> agentDeposits,
                            NulsDomainStaking pocmContract, PocmInfo pi) {
        awardInfo = new ConsensusAwardInfo(Msg.address().toString());
        enableDepositOthers();
        this.userInfo = userInfo;
        this.agentDeposits = agentDeposits;
        this.pocmContract = pocmContract;
        this.pi = pi;
    }

    /**
     * 共识奖励收益处理
     * 委托到其他节点，收益地址只有当前合约地址
     *
     * @param args 区块奖励地址明细 eg. [[address, amount]]
     */
    public void _payable(String[][] args) {
        String[] award = args[0];
        String address = award[0];
        String amount = award[1];
        awardInfo.add(new BigInteger(amount));
    }


    private void enableDepositOthers() {
        require(!enableDepositOthers, "Repeat operation");
        enableDepositOthers = true;
        depositOthersManager = new DepositOthersManager();
        depositOthersManager.modifyMinJoinDeposit(MIN_JOIN_DEPOSIT);
    }

    public String[] addOtherAgent(String agentHash) {
        require(enableDepositOthers, "This feature is not turned on");
        return depositOthersManager.addOtherAgent(agentHash);
    }

    private void remove(String agentHash){
        depositOthersManager.removeAgent(agentHash, this);
    }

    public BigInteger otherDepositLockedAmount() {
        return depositOthersManager.otherDepositLockedAmount();
    }

    /**
     * 增加了押金后，押金数额达到条件后，则委托节点
     * 押金数额未达到条件，则累计总可用押金数额
     *
     * @param value          投资的押金
     */
    public void createOrDepositIfPermitted(BigInteger value) {
        availableAmount = availableAmount.add(value);
        /**
         * 委托其他节点
         */
        if(availableAmount.compareTo(MIN_JOIN_DEPOSIT) >= 0) {
            depositOthersManager.deposit(availableAmount, this);
        }
    }

    /**
     * 当可用金额达到最小可委托金额时，合约拥有者可手动委托合约节点
     */
    public void depositManually() {
        BigInteger amount = availableAmount;
        require(amount.compareTo(MIN_JOIN_DEPOSIT) >= 0, "The available amount is not enough to stake the node");
        require(depositOthersManager.otherAgentsSize() > 0, "No consensus node added");
        /**
         * 委托其他节点
         */
        BigInteger actualDeposit = depositOthersManager.deposit(availableAmount, this);
        require(actualDeposit.compareTo(BigInteger.ZERO) > 0, "All consensus nodes have been fully staked");
    }

    public Set<String> getAgents() {
        return depositOthersManager.getAgents();
    }

    /**
     * 如果合约余额不足，则退出其他节点的委托和合约节点的委托，直到余额足以退还押金
     *
     * @param value 需要退还的押金
     * @return true - 退出委托后余额足够, false - 退出委托，可用余额不足以退还押金
     */
    public boolean withdrawIfPermittedWrapper(BigInteger value) {
        if (availableAmount.compareTo(value) >= 0) {
            availableAmount = availableAmount.subtract(value);
            return true;
        } else {
            value = value.subtract(availableAmount);
            availableAmount = BigInteger.ZERO;
        }
        // 退出委托其他节点的金额
        depositOthersManager.withdrawInner(value, this);
        if (availableAmount.compareTo(value) < 0) {
            return false;
        }
        availableAmount = availableAmount.subtract(value);
        /**
         * 若可用金额足够，则委托其他节点
         */
        if(availableAmount.compareTo(MIN_JOIN_DEPOSIT) >= 0) {
            depositOthersManager.deposit(availableAmount, this);
        }
        return true;
    }

    /**
     * 转移共识奖励金额
     */
    public BigInteger transferConsensusReward() {
        BigInteger availableAward = awardInfo.getAvailableAward();
        require(availableAward.compareTo(BigInteger.ZERO) > 0, "No consensus reward amount available");
        // 清零
        awardInfo.resetAvailableAward();
        BigInteger b = BigInteger.valueOf(100);
        BigInteger project = availableAward.multiply(b.subtract(pi.c)).divide(b);
        BigInteger p = availableAward.subtract(project);
        pendingTreasury = pendingTreasury.add(p);
        if (pendingTreasury.compareTo(MININUM_TRANSFER_AMOUNT) >= 0) {
            pi.treasury.transfer(pendingTreasury);
            pendingTreasury = BigInteger.ZERO;
        }
        if (pi.operatingModel == PocmUtil.NORMAL_MODE) {
            pocmContract.getAwardReceiver().transfer(project);
        } else if (pi.operatingModel == PocmUtil.LP_MODE) {
            //pocmContract.viewLp().transfer(project);
            pocmContract.viewLp().call("amountEnter", null, new String[][]{new String[]{"0"}, new String[]{}}, project);
        }
        return pendingTreasury;
    }

    public BigInteger getPendingTreasury() {
        return pendingTreasury;
    }

    /**
     * 可转移的共识奖励
     */
    public BigInteger getAvailableConsensusReward() {
        return awardInfo.getAvailableAward();
    }

    /**
     * 已转移的共识奖励
     */
    public BigInteger getTransferedConsensusReward() {
        return awardInfo.getTransferedAward();
    }

    /**
     * 获取可委托共识的空闲金额
     */
    public BigInteger getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = availableAmount;
    }

    public void addAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = this.availableAmount.add(availableAmount);
    }
    public void subAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = this.availableAmount.subtract(availableAmount);
    }

    public void repairAmount(BigInteger value) {
        this.availableAmount = this.availableAmount.add(value);
    }

    public void repairConsensusDeposit(BigInteger value) {
        if(enableDepositOthers) {
            this.depositOthersManager.repairDeposit(value);
        }
    }

    public void modifyMinJoinDeposit(BigInteger value) {
        MIN_JOIN_DEPOSIT = value;
        if(enableDepositOthers) {
            depositOthersManager.modifyMinJoinDeposit(MIN_JOIN_DEPOSIT);
        }
    }

    public BigInteger getMinJoinDeposit() {
        return MIN_JOIN_DEPOSIT;
    }

    public void withdrawSpecifiedAmount(BigInteger value) {
        // 退出委托其他节点的金额
        depositOthersManager.withdrawInner(value, this);
    }

    public void emergencyRemoveAgentInner(String agentHash) {
        this.removeAgentInner(agentHash, true);
    }

    private void removeAgentInner(String agentHash, boolean emergency) {
        require(pi.openConsensus, "Consensus is not turned on");
        this.remove(agentHash);
        emit(new PocmRemoveAgentEvent(agentHash));

        //1.共识节点的创建者先领取奖励
        ConsensusAgentDepositInfo agentDepositInfo = agentDeposits.get(agentHash);
        require(agentDepositInfo != null, "This consensus node is not registered");

        String userAddress = agentDepositInfo.getDepositorAddress();
        UserInfo user = userInfo.get(userAddress);
        if (user != null) {
            if (!emergency) {
                // 存在抵押记录，领取奖励
                pocmContract.receiveAwardsByAddress(new Address(userAddress));
            }

            //2.共识节点的创建者退出
            BigInteger agentAmount = user.getAgentAmount();
            boolean openNodeAward = user.isOpenNodeAward();
            // 如果共识节点有糖果奖励，扣减user的可用抵押金，扣减项目的总抵押金
            if (openNodeAward) {
                user.subAmount(BigInteger.ZERO, agentAmount);
                pi.subLpSupply(agentAmount);
            }
            if (user.getAvailableAmount().compareTo(BigInteger.ZERO) > 0) {
                user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));
                user.setAgentAmount(BigInteger.ZERO);
                user.setOpenNodeAward(false);
            } else {
                userInfo.remove(userAddress);
            }
        }
        agentDeposits.remove(agentHash);
    }

    public void removeAgentInner(String agentHash) {
        this.removeAgentInner(agentHash, false);
    }

    public BigInteger consensusEmergencyWithdraw(String joinAgentHash) {
        return depositOthersManager.consensusEmergencyWithdraw(joinAgentHash, this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"availableAmount\":")
                .append('\"').append(toNuls(availableAmount).toPlainString()).append('\"');
        sb.append(",\"awardInfo\":")
                .append(awardInfo.toString());
        if(enableDepositOthers) {
            sb.append(",\"depositOthersManager\":")
                    .append(depositOthersManager.toString());
        }
        sb.append('}');
        return sb.toString();
    }

}
