package io.nuls.contract.pocm.event;


import io.nuls.contract.sdk.Event;

import java.util.List;

/**
 * @author: PierreLuo
 * @date: 2021/9/3
 */
public class PocmQuitDepositEvent implements Event {
    private List<Long> depositNumbers;
    private String  depositorAddress;

    public PocmQuitDepositEvent() {
    }

    public PocmQuitDepositEvent(List<Long> depositNumbers, String depositorAddress) {
        this.depositNumbers = depositNumbers;
        this.depositorAddress = depositorAddress;
    }

}
