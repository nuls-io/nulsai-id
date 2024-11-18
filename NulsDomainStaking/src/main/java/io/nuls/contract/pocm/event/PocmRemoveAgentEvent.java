package io.nuls.contract.pocm.event;

import io.nuls.contract.sdk.Event;

/**
 * @author: tag0313
 * @date: 2019-09-16
 */
public class PocmRemoveAgentEvent implements Event {
    private String hash;

    public PocmRemoveAgentEvent() {
    }

    public PocmRemoveAgentEvent(String hash) {
        this.hash = hash;
    }

}
