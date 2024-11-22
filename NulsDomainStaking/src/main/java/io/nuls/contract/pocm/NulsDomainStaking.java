package io.nuls.contract.pocm;

import io.nuls.contract.pocm.event.*;
import io.nuls.contract.pocm.manager.ConsensusManager;
import io.nuls.contract.pocm.manager.PocmInfo;
import io.nuls.contract.pocm.manager.TotalDepositManager;
import io.nuls.contract.pocm.model.ConsensusAgentDepositInfo;
import io.nuls.contract.pocm.model.CurrentMingInfo;
import io.nuls.contract.pocm.model.UserInfo;
import io.nuls.contract.pocm.ownership.Ownable;
import io.nuls.contract.pocm.util.PocmUtil;
import io.nuls.contract.sdk.*;
import io.nuls.contract.sdk.annotation.*;
import io.nuls.contract.sdk.token.NRC20Wrapper;

import java.math.BigInteger;
import java.util.*;

import static io.nuls.contract.pocm.util.PocmUtil.*;
import static io.nuls.contract.sdk.Utils.*;

/**
 * @author: PierreLuo
 * @date: 2021/8/30
 */
public class NulsDomainStaking extends Ownable implements Contract {

    // POCM合约修订版本
    private final String VERSION = "D01";
    private PocmInfo pi = new PocmInfo();// 合约基础信息
    private Map<String, UserInfo> userInfo = new HashMap<String, UserInfo>();
    private BigInteger allocationAmount = BigInteger.ZERO;//已经分配的Token数量
    private TotalDepositManager totalDepositManager;// 总抵押金额管理器
    private ConsensusManager consensusManager;// 共识管理器
    private boolean isAllocationToken = false;//项目是否初始分配了糖果token
    private boolean isAcceptStaking = false;//是否接受质押
    private Map<String, ConsensusAgentDepositInfo> agentDeposits = new HashMap<String, ConsensusAgentDepositInfo>();
    private boolean initialized = false;
    private Address candyTokenCopy;

    public NulsDomainStaking() {
        if (Msg.sender().toString().startsWith("NULS")) {
            candyTokenCopy = new Address("NULSd6Hgv3Y49CHapt5qL4pCiEX8x3ZHrh6ie");
        } else {
            candyTokenCopy = new Address("tNULSeBaMwGu9Xt8YLBiCaemgHciekfh1ScfkD");
        }
    }

    /**
     *
     * @param treasury                          国库
     * @param official                          管理者
     * @param domain                            domain合约
     */
    public void initialize(
                        Address treasury,
                        Address official,
                        Address domain) {
        onlyOwner();
        if (initialized) {
            revert("PocmInitialized");
        }
        initialized = true;
        super.setOfficial(official.toString());
        super.setAwardReceiver(domain);
        pi.treasury = treasury;
        String candyTokenAddress = deploy(new String[]{Msg.address().toString(), "i" + Block.timestamp()}, candyTokenCopy, new String[]{"domainStaking", "DOMAIN", "1000010000000", "18"});
        Address candyToken = new Address(candyTokenAddress);
        pi.isNRC20Candy = true;
        pi.candyTokenWrapper = new NRC20Wrapper(candyToken);
        pi.candyTokenWrapper.transfer(Msg.sender(), BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(10000000)));
        pi.candyToken = candyToken;
        pi.candyAssetChainId = 0;
        pi.candyAssetId = 0;
        pi.candyPerBlock = BigInteger.TEN.pow(16);
        pi.candySupply = BigInteger.TEN.pow(18).multiply(BigInteger.valueOf(1000000000000L));
        pi.lockedTokenDay = 0;
        pi.minimumStaking = MININUM_TRANSFER_AMOUNT;
        pi.maximumStaking = ONE_NULS.multiply(TEN_THOUSAND).multiply(TEN_THOUSAND);
        this.totalDepositManager = new TotalDepositManager();
        this.openConsensusInner();
        pi.openAwardConsensusNodeProvider = false;
        pi.authorizationCode = "domain";
        pi.accPerShare = BigInteger.ZERO;
        pi.lockedTime = pi.lockedTokenDay * pi.TIMEPERDAY;
        pi.lastRewardBlock = Block.number();
        pi.endBlock = Block.number() + 12717449280L;
        pi.operatingModel = 0;
        pi.rewardDrawRatioForLp = 0;
        setPocmInfo(pi);
        emit(new PocmCreateContract17Event(
                candyToken.toString(),
                0, 0,
                pi.candyPerBlock, pi.candySupply,
                0,
                pi.minimumStaking,
                pi.maximumStaking,
                true, false, "domain", 0, 0));
    }

    @Override
    public void _payable() {
        revert("Do not accept direct transfers");
    }

    @Override
    @PayableMultyAsset
    public void _payableMultyAsset() {
        require(!pi.isNRC20Candy, "transfer: do not accept direct transfers");
        MultyAssetValue[] values = Msg.multyAssetValues();
        if (values == null) return;
        require(values.length == 1, "transfer: asset not good");
        MultyAssetValue value = values[0];
        require(value.getAssetChainId() == pi.candyAssetChainId && value.getAssetId() == pi.candyAssetId, "transfer: asset not good");
    }

    /**
     * 共识奖励收益处理
     * 创建的节点的100%佣金比例，收益地址只有当前合约地址
     * (底层系统调用，不能被任何人调用)
     *
     * @param args 区块奖励地址明细 eg. [[address, amount], [address, amount], ...]
     */
    @Override
    @Payable
    public void _payable(String[][] args) {
        consensusManager._payable(args);
    }

    public void addCandySupply(BigInteger _amount) {
        onlyOfficial();
        pi.candySupply = pi.candySupply.add(_amount);
        emit(new PocmCandySupplyEvent(pi.candySupply));
    }

    @Payable
    public void depositForOwn() {
        require(isAllocationToken(), "No enough candy token in the contract");
        require(isAcceptStaking(), "Cannot staking, please check the contract");
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        BigInteger _amount = Msg.value();
        // 退还抵押金的小数位
        BigInteger decimalValue = PocmUtil.extractDecimal(_amount);
        boolean hasDecimal = decimalValue.compareTo(BigInteger.ZERO) > 0;
        if (hasDecimal) {
            // 防止退回的小数金额太小
            if (decimalValue.compareTo(MININUM_TRANSFER_AMOUNT) < 0) {
                decimalValue = decimalValue.add(ONE_NULS);
            }
            _amount = _amount.subtract(decimalValue);
            require(decimalValue.add(_amount).compareTo(Msg.value()) == 0, "Decimal parsing error, staking: " + Msg.value());
        }
        require(_amount.compareTo(pi.minimumStaking) >= 0, "amount not good[minimum]");
        require(_amount.compareTo(pi.maximumStaking) <= 0, "amount not good[maximum]");
        updatePool();
        if (user != null) {
            // 领取奖励
            this.receiveInternal(sender, user);
        }

        // 90% available for staking
        //BigDecimal bigDecimalValue = new BigDecimal(_amount);
        //BigInteger availableAmount = AVAILABLE_PERCENT.multiply(bigDecimalValue).toBigInteger();
        // 100% available for staking
        BigInteger availableAmount = _amount;
        long blockNumber = Block.number();

        if (user == null) {
            user = new UserInfo(_amount, availableAmount, BigInteger.ZERO, blockNumber);
            this.userInfo.put(senderAddress, user);
        } else {
            user.addAmount(_amount, availableAmount);
            user.setLastDepositHeight(blockNumber);
        }
        require(user.getAmount().compareTo(pi.maximumStaking) <= 0, "user amount not good[maximum]");
        pi.addLpSupply(availableAmount);
        user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));

        totalDepositManager.add(availableAmount);

        // 退还抵押金的小数位
        if (hasDecimal) {
            sender.transfer(decimalValue);
        }
        // 抵押事件
        emit(new DepositDetailInfoEvent(_amount, 0, _amount, availableAmount, _amount.subtract(availableAmount), blockNumber, senderAddress));
    }

    public void receiveAwards(String unused) {
        this.receiveAwardsByAddress(Msg.sender());
    }

    public void receiveAwardsByAddress(Address address) {
        require(address != null, "empty address");
        String addressStr = address.toString();
        UserInfo user = this.userInfo.get(addressStr);
        require(user != null, "user not exist");
        updatePool();
        this.receiveInternal(address, user);
        user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));
    }

    // Withdraw LP tokens from pool.
    public void withdraw(BigInteger _amount) {
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        require(user != null, "user not exist");
        this.withdrawByUser(sender, user, _amount);
    }

    // Withdraw without caring about rewards. EMERGENCY ONLY.
    public void emergencyWithdraw() {
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        require(user != null, "user not exist");
        this.emergencyWithdrawByUser(sender, user);
    }

    // Withdraw without caring about rewards. EMERGENCY ONLY.
    public void giveUp() {
        emergencyWithdraw();
    }


    public void quit(String unused) {
        Address sender = Msg.sender();
        String senderAddress = sender.toString();
        UserInfo user = this.userInfo.get(senderAddress);
        require(user != null, "user not exist");
        this.withdrawByUser(sender, user, user.getAmount());
    }

    public void updateTotalAllocation() {
        BigInteger balance = pi.candyTokenWrapper.balanceOf(Msg.address());
        if (balance.compareTo(BigInteger.ZERO) > 0) {
            this.isAcceptStaking = true;
        } else {
            this.isAcceptStaking = false;
        }
    }

    public void clearContract() {
        onlyOwner();
        BigInteger balance = Msg.address().balance();
        require(balance.compareTo(ONE_NULS) <= 0, "The balance cannot be greater than 1 NULS");
        require(balance.compareTo(BigInteger.ZERO) > 0, "The balance is zero, no need to clear");
        contractCreator.transfer(balance);
    }


    public void refreshEndBlock(BigInteger value) {
        onlyOfficial();
        BigInteger blockCount = value.divide(pi.candyPerBlock);
        pi.endBlock += blockCount.longValue();
    }

    public void changeAwardReceiver(@Required Address _awardReceiver) {
        onlyOfficial();
        super.setAwardReceiver(_awardReceiver);
    }
    /**
     * 开启共识功能
     */
    public void openConsensus() {
        onlyOfficial();
        openConsensusInner();
    }

    public void closeConsensus() {
        onlyOfficial();
        require(pi.openConsensus, "Consensus has been turned off");
        pi.openConsensus = false;
        totalDepositManager.closeConsensus();
    }

    public void modifyMinJoinDeposit(BigInteger value) {
        onlyOfficial();
        require(pi.openConsensus, "Consensus is not turned on");
        require(value.compareTo(pi._2000_NULS) >= 0, "Amount too small");
        consensusManager.modifyMinJoinDeposit(value);
    }

    public void consensusWithdrawSpecifiedAmount(BigInteger value) {
        onlyOfficial();
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.withdrawSpecifiedAmount(value);
    }

    public void repairConsensus(BigInteger value) {
        onlyOfficial();
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.repairAmount(value);
    }

    public void repairConsensusDeposit(BigInteger value) {
        onlyOfficial();
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.repairConsensusDeposit(value);
    }

    public void repairTotalDepositManager(BigInteger value) {
        onlyOfficial();
        totalDepositManager.repairAmount(value);
    }

    /**
     * 手动把闲置的抵押金委托到共识节点
     */
    public void depositConsensusManually() {
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.depositManually();
    }

    /**
     * 合约拥有者获取共识奖励金额
     */
    public void transferConsensusRewardByOwner() {
        require(pi.openConsensus, "Consensus is not turned on");
        consensusManager.transferConsensusReward();
    }

    /**
     * 添加其他节点的共识信息
     *
     * @param agentHash 其他共识节点的hash
     */
    public void addOtherAgent(String agentHash) {
        onlyOwnerOrOfficial();
        require(pi.openConsensus, "Consensus is not turned on");
        require(isAllocationToken() && isAcceptStaking(), "No enough candy token in the contract");
        String[] agentInfo = consensusManager.addOtherAgent(agentHash);
        String agentAddress = agentInfo[0];
        Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
        for (ConsensusAgentDepositInfo agentDepositInfo : agentDepositInfos) {
            require(!agentDepositInfo.getDepositorAddress().equals(agentAddress), "The creator address of the node being added conflicts with the creator address of the added node");
        }
        BigInteger agentValue = new BigInteger(agentInfo[3]);
        emit(new PocmAgentEvent(agentHash, agentValue, pi.openAwardConsensusNodeProvider));
        BigInteger availableValue = BigInteger.ZERO;
        updatePool();
        long currentHeight = Block.number();
        UserInfo user = this.userInfo.get(agentAddress);
        if (user == null) {
            user = new UserInfo(BigInteger.ZERO, availableValue, BigInteger.ZERO, currentHeight);
            user.setAgentAmount(agentValue);
            this.userInfo.put(agentAddress, user);
        } else {
            // 存在抵押记录，领取奖励
            this.receiveInternal(new Address(agentAddress), user);
            //更新抵押信息
            user.addAmount(BigInteger.ZERO, availableValue);
            user.setAgentAmount(agentValue);
        }
        user.setOpenNodeAward(pi.openAwardConsensusNodeProvider);
        pi.addLpSupply(availableValue);
        user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));

        ConsensusAgentDepositInfo agentDepositInfo = new ConsensusAgentDepositInfo(agentHash, agentAddress, 0);
        agentDeposits.put(agentHash, agentDepositInfo);
    }

    /**
     * 删除节点信息
     *
     * @param agentHash 其他共识节点的hash
     */
    public void removeAgent(String agentHash) {
        onlyOfficial();
        consensusManager.removeAgentInner(agentHash);
    }

    /**
     * 紧急删除节点信息
     *
     * @param agentHash 其他共识节点的hash
     */
    public void emergencyRemoveAgent(String agentHash) {
        onlyOfficial();
        updatePool();
        consensusManager.emergencyRemoveAgentInner(agentHash);
    }

    public void quitAll() {
        onlyOfficial();
        require(consensusManager.getAgents() == null, "Please remove the consensus node first");
        boolean hasAgents = !agentDeposits.isEmpty();
        Set<String> skippedSet = new HashSet<String>();
        if (hasAgents) {
            Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
            for (ConsensusAgentDepositInfo info : agentDepositInfos) {
                skippedSet.add(info.getDepositorAddress());
            }
        }
        Set<String> userSet = this.userInfo.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            if (hasAgents && skippedSet.contains(user)) {
                continue;
            }
            this.quitByUser(user);
        }
    }

    public void quitByAddresses(String[] addresses) {
        onlyOfficial();
        for (String user : addresses) {
            this.quitByUser(user);
        }
    }

    public void receiveAll() {
        Set<String> userSet = this.userInfo.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            this.receiveAwardsByAddress(new Address(user));
        }
    }

    public void receiveByAddresses(String[] addresses) {
        for (String user : addresses) {
            this.receiveAwardsByAddress(new Address(user));
        }
    }

    public void giveUpAll() {
        onlyOfficial();
        require(consensusManager.getAgents() == null, "Please remove the consensus node first");
        boolean hasAgents = !agentDeposits.isEmpty();
        Set<String> skippedSet = new HashSet<String>();
        if (hasAgents) {
            Collection<ConsensusAgentDepositInfo> agentDepositInfos = agentDeposits.values();
            for (ConsensusAgentDepositInfo info : agentDepositInfos) {
                skippedSet.add(info.getDepositorAddress());
            }
        }
        Set<String> userSet = this.userInfo.keySet();
        List<String> userList = new ArrayList<String>(userSet);
        for (String user : userList) {
            if (hasAgents && skippedSet.contains(user)) {
                continue;
            }
            this.giveUpByUser(user);
        }
    }

    public void giveUpByAddresses(String[] addresses) {
        onlyOfficial();
        for (String user : addresses) {
            this.giveUpByUser(user);
        }
    }

    public BigInteger consensusEmergencyWithdraw(String joinAgentHash) {
        onlyOfficial();
        return consensusManager.consensusEmergencyWithdraw(joinAgentHash);
    }

    public void updateC(int c) {
        onlyOfficial();
        require(c >= 10 && c < 100, "10 <= Value < 100.");
        pi.c = BigInteger.valueOf(c);
    }

    @View
    public int totalDepositAddressCount() {
        return this.userInfo.size();
    }

    @View
    public String totalDeposit() {
        return toNuls(totalDepositManager.getTotalDeposit()).toPlainString();
    }

    @View
    public String totalDepositDetail() {
        return totalDepositManager.getTotalDepositDetail();
    }

    @View
    public String getAuthorizationCode() {
        return pi.authorizationCode;
    }
    @View
    public String getTreasury() {
        return pi.treasury.toString();
    }

    @View
    public String getPendingTreasury() {
        return consensusManager.getPendingTreasury().toString();
    }

    @View
    public BigInteger minimumStaking() {
        return pi.minimumStaking;
    }

    @View
    public BigInteger lpSupply() {
        return pi.getLpSupply();
    }

    @View
    public int getLockedDay() {
        return pi.lockedTokenDay;
    }

    @View
    public String version() {
        return VERSION;
    }

    @View
    public BigInteger getCandySupply() {
        return pi.candySupply;
    }

    @View
    public long getEndBlock() {
        return pi.endBlock;
    }
    /**
     * 查询可领取的共识奖励金额
     */
    @View
    public String ownerAvailableConsensusAward() {
        require(pi.openConsensus, "Consensus is not turned on");
        BigInteger b = BigInteger.valueOf(100);
        return consensusManager.getAvailableConsensusReward().multiply(b.subtract(pi.c)).divide(b).toString();
    }

    /**
     * 查询共识总奖励金额
     */
    @View
    public String ownerTotalConsensusAward() {
        require(pi.openConsensus, "Consensus is not turned on");
        BigInteger b = BigInteger.valueOf(100);
        return consensusManager.getAvailableConsensusReward().add(consensusManager.getTransferedConsensusReward()).multiply(b.subtract(pi.c)).divide(b).toString();
    }

    /**
     * 查询可委托共识的空闲金额
     */
    @View
    public String freeAmountForConsensusDeposit() {
        require(pi.openConsensus, "Consensus is not turned on");
        return consensusManager.getAvailableAmount().toString();
    }

    @View
    public String getMinJoinDeposit() {
        require(pi.openConsensus, "Consensus is not turned on");
        return consensusManager.getMinJoinDeposit().toString();
    }

    /**
     * 查询合约当前所有信息
     */
    @View
    public String wholeConsensusInfo() {
        String totalDepositDetail = totalDepositDetail();
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"totalDepositDetail\":")
                .append('\"').append(totalDepositDetail).append('\"');
        if (pi.openConsensus) {
            sb.append(",\"consensusManager\":")
                    .append(consensusManager == null ? "0" : consensusManager.toString());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * 查找用户的抵押信息
     *
     * @return
     */
    @View
    @JSONSerializable
    public UserInfo getDepositInfo(Address address) {
        return this.userInfo.get(address.toString());
    }

    /**
     * 领取质押者参与质押的交易未领取的收益
     *
     * @param stakingAddress 质押者账户地址
     * @return
     */
    @View
    public String calcUnReceiveAwards(@Required Address stakingAddress, String unused) {
        return this.pendingToken(stakingAddress);
    }

    /**
     * 未分配的Token数量
     *
     * @return
     */
    @View
    public String calcUnAllocationTokenAmount() {
        return pi.candyTokenWrapper.balanceOf(Msg.address()).toString();
    }

    /**
     * 已分配的Token数量
     *
     * @return
     */
    @View
    public String getAllocationAmount() {
        return this.allocationAmount.toString();
    }

    // View function to see pending token on frontend.
    @View
    public String pendingToken(Address _user) {
        String userAddress = _user.toString();
        UserInfo user = this.userInfo.get(userAddress);
        require(user != null, "user not exist");
        long blockNumber = Block.number();
        blockNumber = blockNumber < pi.endBlock ? blockNumber : pi.endBlock;
        if (blockNumber > pi.lastRewardBlock && pi.getLpSupply().compareTo(BigInteger.ZERO) > 0) {
            BigInteger reward = BigInteger.valueOf(blockNumber - pi.lastRewardBlock).multiply(pi.candyPerBlock);
            pi.accPerShare = pi.accPerShare.add(reward.multiply(pi._1e12).divide(pi.getLpSupply()));   // 此处乘以1e12，在下面会除以1e12
        }
        BigInteger pendingReward = user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12).subtract(user.getRewardDebt());
        if (pi.operatingModel == LP_MODE) {
            BigInteger lpMode = pendingReward.multiply(BigInteger.valueOf(pi.rewardDrawRatioForLp)).divide(TEN_THOUSAND);
            return pendingReward.subtract(lpMode).toString();
        }
        return pendingReward.toString();
    }

    private BigInteger checkCandyBalance() {
        BigInteger candyBalance = pi.candyTokenWrapper.balanceOf(Msg.address());
        require(candyBalance.compareTo(BigInteger.ZERO) > 0, "No enough candy token in the contract");
        return candyBalance;
    }

    private void receiveInternal(Address sender, UserInfo user) {
        BigInteger candyBalance = checkCandyBalance();
        if (user.getAvailableAmount().compareTo(BigInteger.ZERO) > 0) {
            BigInteger pending = user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12).subtract(user.getRewardDebt());
            if (pending.compareTo(BigInteger.ZERO) > 0) {
                // 发放的奖励 <= 糖果余额
                BigInteger amount;
                if (candyBalance.compareTo(pending) > 0) {
                    amount = pending;
                } else {
                    amount = candyBalance;
                    this.isAcceptStaking = false;
                }
                long lockedTime = pi.lockedTime;
                if (pi.isNRC20Candy) {
                    lockedTime += Block.timestamp();
                }
                // 奖励领取事件
                ArrayList<CurrentMingInfo> list = new ArrayList<CurrentMingInfo>();
                if (pi.operatingModel == LP_MODE) {
                    BigInteger lpMode = amount.multiply(BigInteger.valueOf(pi.rewardDrawRatioForLp)).divide(TEN_THOUSAND);
                    BigInteger userAmount = amount.subtract(lpMode);
                    pi.candyTokenWrapper.transferLocked(sender, userAmount, lockedTime);
                    if (pi.isNRC20Candy) {
                        pi.candyTokenWrapper.approve(this.viewLp(), lpMode);
                        this.viewLp().call("amountEnter", null, new String[][]{new String[]{lpMode.toString()}, new String[]{pi.candyToken.toString()}}, BigInteger.ZERO);
                    } else {
                        this.viewLp().callWithReturnValue("amountEnter", null, new String[][]{new String[]{"0"}, new String[]{}}, BigInteger.ZERO,
                                new MultyAssetValue[]{new MultyAssetValue(lpMode, pi.candyAssetChainId, pi.candyAssetId)});
                    }
                    list.add(new CurrentMingInfo(0, userAmount, sender.toString(), 0));
                    list.add(new CurrentMingInfo(0, lpMode, this.viewLp().toString(), 0));
                } else if (pi.operatingModel == NORMAL_MODE) {
                    pi.candyTokenWrapper.transferLocked(sender, amount, lockedTime);
                    list.add(new CurrentMingInfo(0, amount, sender.toString(), 0));
                }
                this.allocationAmount = this.allocationAmount.add(amount);
                emit(new CurrentMiningInfoEvent(list));
            }
        }
    }

    private void withdrawByUser(Address sender, UserInfo user, BigInteger _amount) {
        String senderAddress = sender.toString();
        require(_amount.compareTo(BigInteger.ZERO) > 0, "withdraw: amount not good");
        require(user.getAmount().compareTo(_amount) >= 0, "withdraw: amount not good");
        updatePool();
        // 领取奖励
        this.receiveInternal(sender, user);

        //BigInteger available = AVAILABLE_PERCENT.multiply(new BigDecimal(_amount)).toBigInteger();
        BigInteger available = _amount;
        boolean isEnoughBalance = totalDepositManager.subtract(available);
        require(isEnoughBalance, "The balance is not enough to refund the staking, please contact the project party, the staking: " + available);
        user.subAmount(_amount, available);
        pi.subLpSupply(available);
        user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));
        if (_amount.compareTo(BigInteger.ZERO) > 0) {
            sender.transfer(_amount);
        }
        if (user.getAvailableAmount().compareTo(BigInteger.ZERO) == 0 && user.getAgentAmount().compareTo(BigInteger.ZERO) == 0) {
            this.userInfo.remove(senderAddress);
        }
        // 提现事件
        emit(new PocmWithdrawEvent(senderAddress, _amount.toString()));
    }

    private void emergencyWithdrawByUser(Address sender, UserInfo user) {
        String senderAddress = sender.toString();
        BigInteger _amount = user.getAmount();
        //BigInteger available = AVAILABLE_PERCENT.multiply(new BigDecimal(_amount)).toBigInteger();
        BigInteger available = _amount;
        boolean isEnoughBalance = totalDepositManager.subtract(available);
        require(isEnoughBalance, "The balance is not enough to refund the staking, please contact the project party, the staking: " + available);
        if (_amount.compareTo(BigInteger.ZERO) > 0) {
            sender.transfer(_amount);
            user.subAmount(_amount, available);
            // 紧急提现，退出抵押事件，避免节点创建者紧急提现，QuitDepositEvent的事件在后台可能会把正常抵押删除
            List<Long> stakingNumbers = new ArrayList<Long>();
            stakingNumbers.add(0L);
            emit(new PocmQuitDepositEvent(stakingNumbers, senderAddress));
        }
        if (user.getAvailableAmount().compareTo(BigInteger.ZERO) == 0) {
            this.userInfo.remove(senderAddress);
        } else {
            updatePool();
            user.setRewardDebt(user.getAvailableAmount().multiply(pi.accPerShare).divide(pi._1e12));
        }
        pi.subLpSupply(available);
    }

    private boolean isAcceptStaking() {
        return isAcceptStaking;
    }

    private boolean isAllocationToken() {
        if (!isAllocationToken) {
            BigInteger balance = pi.candyTokenWrapper.balanceOf(Msg.address());
            if (balance.compareTo(BigInteger.ZERO) > 0) {
                isAllocationToken = true;
                isAcceptStaking = true;
            }
        }
        return isAllocationToken;
    }

    private void giveUpByUser(String userAddress) {
        UserInfo userInfo = this.userInfo.get(userAddress);
        if (userInfo == null) {
            return;
        }
        this.emergencyWithdrawByUser(new Address(userAddress), userInfo);
    }

    private void quitByUser(String userAddress) {
        UserInfo userInfo = this.userInfo.get(userAddress);
        if (userInfo == null) {
            return;
        }
        this.withdrawByUser(new Address(userAddress), userInfo, userInfo.getAmount());
    }

    private void updatePool() {
        long blockNumber = Block.number();
        if (blockNumber <= pi.lastRewardBlock) {
            return;
        }
        if (pi.getLpSupply().compareTo(BigInteger.ZERO) == 0) {
            pi.lastRewardBlock = blockNumber;
            return;
        }
        blockNumber = blockNumber < pi.endBlock ? blockNumber : pi.endBlock;
        BigInteger reward = BigInteger.valueOf(blockNumber - pi.lastRewardBlock).multiply(pi.candyPerBlock);
        pi.accPerShare = pi.accPerShare.add(reward.multiply(pi._1e12).divide(pi.getLpSupply()));
        pi.lastRewardBlock = blockNumber;
    }

    private void openConsensusInner() {
        require(!pi.openConsensus, "Consensus has been turned on");
        pi.openConsensus = true;
        if (this.consensusManager == null) {
            this.consensusManager = new ConsensusManager(this.userInfo, this.agentDeposits, this, this.pi);
        }
        this.totalDepositManager.openConsensus(this.consensusManager);
    }

}