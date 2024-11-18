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
package io.nuls.contract.model;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

/**
 * @author: PierreLuo
 * @date: 2024/11/14
 */
public class UserInfo {
    private String mainDomains;
    private final Set<String> activeDomains;
    private final Set<String> inactiveDomains;
    private BigInteger pending;
    private BigInteger rewardDebt;

    public UserInfo() {
        this.activeDomains = new HashSet<String>();
        this.inactiveDomains = new HashSet<String>();
        this.pending = BigInteger.ZERO;
        this.rewardDebt = BigInteger.ZERO;
    }

    public String getMainDomains() {
        return mainDomains;
    }

    public void setMainDomains(String mainDomains) {
        this.mainDomains = mainDomains;
    }

    public int getActiveDomainsSize() {
        return activeDomains.size();
    }

    public void addActiveDomains(String domain) {
        if (mainDomains == null) {
            mainDomains = domain;
        }
        this.activeDomains.add(domain);
    }

    public void removeActiveDomains(String domain) {
        this.activeDomains.remove(domain);
        if (mainDomains.equals(domain)) {
            mainDomains = null;
        }
    }

    public void addInactiveDomains(String domain) {
        if (mainDomains == null) {
            mainDomains = domain;
        }
        this.inactiveDomains.add(domain);
    }

    public void removeInactiveDomains(String domain) {
        this.inactiveDomains.remove(domain);
        if (mainDomains.equals(domain)) {
            mainDomains = null;
        }
    }

    public int getInactiveDomainsSize() {
        return inactiveDomains.size();
    }

    public BigInteger getRewardDebt() {
        return rewardDebt;
    }

    public void setRewardDebt(BigInteger rewardDebt) {
        this.rewardDebt = rewardDebt;
    }

    public BigInteger getPending() {
        return pending;
    }

    public void setPending(BigInteger pending) {
        this.pending = pending;
    }
}
