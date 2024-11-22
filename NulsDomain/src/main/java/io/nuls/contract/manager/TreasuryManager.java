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
package io.nuls.contract.manager;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.entity.Staking;

import java.math.BigInteger;

import static io.nuls.contract.sdk.Utils.require;
import static java.math.BigInteger.*;


/**
 * @author: PierreLuo
 * @date: 2019-05-14
 */
public class TreasuryManager {
    public BigInteger ONE_NULS = valueOf(100000000);
    public BigInteger _2000_NULS = ONE_NULS.multiply(valueOf(2000));
    public BigInteger MININUM_TRANSFER_AMOUNT = BigInteger.TEN.pow(6);

    // 总抵押金额
    private BigInteger available;
    private BigInteger stakingAmount;
    private Address staking;
    private Address treasury;
    private BigInteger feeRate;

    public TreasuryManager() {
        this.available = ZERO;
        this.stakingAmount = ZERO;
        this.feeRate = valueOf(20);
    }

    public void setStaking(Address staking) {
        this.staking = staking;
    }

    public void setTreasury(Address treasury) {
        this.treasury = treasury;
    }

    public void setFeeRate(BigInteger feeRate) {
        this.feeRate = feeRate;
    }

    public Address getStaking() {
        return staking;
    }

    public Address getTreasury() {
        return treasury;
    }

    public BigInteger getTotal() {
        return this.available.add(this.stakingAmount);
    }
    public BigInteger getAvailable() {
        return this.available;
    }

    public BigInteger getStakingAmount() {
        return this.stakingAmount;
    }

    public void add(BigInteger value) {
        BigInteger fee = value.multiply(feeRate).divide(valueOf(100));
        value = value.subtract(fee);
        this.treasury.transfer(fee);

        Staking staking = new Staking(this.staking);
        this.available = this.available.add(value);
        if (available.compareTo(_2000_NULS) >= 0) {
            staking.depositForOwn(available);
            stakingAmount = stakingAmount.add(available);
            available = ZERO;
        }
    }

    /*public void subtract(BigInteger value) {
        Pocm pocm = new Pocm(staking);
        BigInteger avalilable = total.subtract(stakingAmount);
        if (avalilable.compareTo(value) < 0) {
            BigInteger withdrawAmount = value.subtract(avalilable);
            pocm.withdraw(withdrawAmount);
            stakingAmount = stakingAmount.subtract(withdrawAmount);
        }
        require(Msg.address().balance().compareTo(value) >= 0 && total.compareTo(value) >= 0, "Insufficient treasury");
        this.total = this.total.subtract(value);
        pocm.transferConsensusRewardByOwner();
    }*/

}
