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
package io.nuls.contract.pocm.model;

import java.math.BigInteger;

/**
 * @author: PierreLuo
 * @date: 2021/8/31
 */
public class UserInfo {

    private BigInteger amount;
    private BigInteger rewardDebt;
    private BigInteger availableAmount;// 100%
    private long lastDepositHeight;// last deposit height
    private BigInteger agentAmount;//共识保证金数量
    private boolean openNodeAward;//共识节点是否可获得糖果奖励

    public UserInfo(BigInteger amount, BigInteger available, BigInteger rewardDebt, long lastDepositHeight) {
        this.amount = amount;
        this.availableAmount = available;
        this.rewardDebt = rewardDebt;
        this.lastDepositHeight = lastDepositHeight;
        this.agentAmount = BigInteger.ZERO;
        this.openNodeAward = false;
    }

    public void addAmount(BigInteger amount, BigInteger available) {
        this.amount = this.amount.add(amount);
        this.availableAmount = this.availableAmount.add(available);
    }

    public void subAmount(BigInteger amount, BigInteger available) {
        this.amount = this.amount.subtract(amount);
        this.availableAmount = this.availableAmount.subtract(available);
    }

    public BigInteger getAmount() {
        return amount;
    }

    public void setAmount(BigInteger amount) {
        this.amount = amount;
    }

    public BigInteger getRewardDebt() {
        return rewardDebt;
    }

    public void setRewardDebt(BigInteger rewardDebt) {
        this.rewardDebt = rewardDebt;
    }

    public BigInteger getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigInteger availableAmount) {
        this.availableAmount = availableAmount;
    }

    public long getLastDepositHeight() {
        return lastDepositHeight;
    }

    public void setLastDepositHeight(long lastDepositHeight) {
        this.lastDepositHeight = lastDepositHeight;
    }

    public BigInteger getAgentAmount() {
        return agentAmount;
    }

    public void setAgentAmount(BigInteger agentAmount) {
        this.agentAmount = agentAmount;
    }

    public void addAgentAmount(BigInteger agentAmount) {
        this.agentAmount = this.agentAmount.add(agentAmount);
    }

    public void subAgentAmount(BigInteger agentAmount) {
        this.agentAmount = this.agentAmount.subtract(agentAmount);
    }

    public boolean isOpenNodeAward() {
        return openNodeAward;
    }

    public void setOpenNodeAward(boolean openNodeAward) {
        this.openNodeAward = openNodeAward;
    }
}
