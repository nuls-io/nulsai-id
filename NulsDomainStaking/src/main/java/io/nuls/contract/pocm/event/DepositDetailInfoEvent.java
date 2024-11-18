package io.nuls.contract.pocm.event;


import io.nuls.contract.sdk.Event;

import java.math.BigInteger;

/**
 * @author: PierreLuo
 * @date: 2021/9/3
 */
public class DepositDetailInfoEvent implements Event {
    private BigInteger depositValue;
    private long depositNumber;
    private BigInteger depositAmount;
    private BigInteger availableAmount;
    private BigInteger lockedAmount;
    private long depositHeight;
    private String miningAddress;

    public DepositDetailInfoEvent() {
    }

    public DepositDetailInfoEvent(BigInteger depositValue, long depositNumber, BigInteger depositAmount, BigInteger availableAmount, BigInteger lockedAmount, long depositHeight, String miningAddress) {
        this.depositValue = depositValue;
        this.depositNumber = depositNumber;
        this.depositAmount = depositAmount;
        this.availableAmount = availableAmount;
        this.lockedAmount = lockedAmount;
        this.depositHeight = depositHeight;
        this.miningAddress = miningAddress;
    }

}
