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
package io.nuls.contract;

import io.nuls.contract.entity.NRC721;
import io.nuls.contract.entity.Staking;
import io.nuls.contract.event.*;
import io.nuls.contract.manager.TreasuryManager;
import io.nuls.contract.model.NextId;
import io.nuls.contract.model.UserInfo;
import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Contract;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.JSONSerializable;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;
import io.nuls.contract.utils.ReentrancyGuard;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static io.nuls.contract.sdk.Utils.*;

/**
 * @author: PierreLuo
 * @date: 2019-06-10
 */
public class NulsDomain extends ReentrancyGuard implements Contract {

    private final Map<BigInteger, String> domains = new HashMap<BigInteger, String>();
    private final Map<String, BigInteger> domainIndexes = new HashMap<String, BigInteger>();
    private final Map<String, NextId> domainNextIds = new HashMap<String, NextId>();
    private final Map<BigInteger, Address> token721ByStartIds = new HashMap<BigInteger, Address>();
    private BigInteger rewardCount = BigInteger.ZERO;
    private final Map<String, Boolean> domainAwards = new HashMap<String, Boolean>();
    private final Map<String, Address> domainSuffixFor721Map = new HashMap<String, Address>();
    private final Map<Address, String> token721ForSuffixMap = new HashMap<Address, String>();
    private final Map<Address, UserInfo> userDomains = new HashMap<Address, UserInfo>();
    private final Map<Integer, BigInteger> domainPrice = new HashMap<Integer, BigInteger>();
    private BigInteger defaultPrice;
    private int defaultPriceLength;
    private final TreasuryManager treasuryManager;
    private BigInteger accPerShare = BigInteger.ZERO;
    private BigInteger lastAward = BigInteger.ZERO;
    private final BigInteger _1e12 = BigInteger.TEN.pow(12);
    private boolean initialized = false;
    private BigInteger lastDefaultStartId;
    private final BigInteger _100000 = BigInteger.valueOf(100000);

    public NulsDomain() {
        this.treasuryManager = new TreasuryManager();
        domainPrice.put(1, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(10000)));
        domainPrice.put(2, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(5000)));
        domainPrice.put(3, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(3000)));
        domainPrice.put(4, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(1000)));
        domainPrice.put(5, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(500)));
        domainPrice.put(6, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(200)));
        this.defaultPrice = treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(100));
        this.defaultPriceLength = 7;
    }

    public void initialize(
            Address staking,
            Address treasury,
            Address nulsSuffix721) {
        onlyOwner();
        if (initialized) {
            revert("DomainInitialized");
        }
        initialized = true;
        treasuryManager.setStaking(staking);
        treasuryManager.setTreasury(treasury);
        lastDefaultStartId = BigInteger.ZERO;
        this.addSuffixInfo("nuls", nulsSuffix721, lastDefaultStartId);
    }

    protected void onlyDomain721() {
        require(token721ForSuffixMap.containsKey(Msg.sender()), "Only domain721 can call it.");
    }

    @Payable
    @Override
    public void _payable() {
        require(Msg.sender().isContract(), "Do not accept direct transfers");
    }

    public void addDomainSuffix(String suffix, Address _721) {
        onlyOwner();
        lastDefaultStartId = lastDefaultStartId.add(_100000);
        if (suffix == null || suffix.length() == 0) {
            NRC721 nrc721 = new NRC721(_721);
            suffix = nrc721.name();
        }
        this.addSuffixInfo(suffix, _721, lastDefaultStartId);
    }

    public void changeDomainPrice(int length, BigInteger price) {
        onlyOwner();
        require(length > 0 && length <= 64, "error length");
        require(price.compareTo(treasuryManager.ONE_NULS) >= 0, "Error price");

        if (length >= defaultPriceLength) {
            require(length == defaultPriceLength, "Can only be added in order");
            require(price.compareTo(defaultPrice) > 0, "Price must be greater than the default price");
            this.defaultPriceLength++;
            domainPrice.put(length, price);
        } else {
            int before = length - 1;
            int after = length + 1;
            BigInteger beforePrice;
            BigInteger afterPrice;
            if (before == 0) {
                beforePrice = treasuryManager.ONE_NULS.multiply(treasuryManager.ONE_NULS);
            } else {
                beforePrice = domainPrice.get(before);
            }
            if (after == defaultPriceLength) {
                afterPrice = defaultPrice;
            } else {
                afterPrice = domainPrice.get(after);
            }
            require(price.compareTo(beforePrice) < 0 && price.compareTo(afterPrice) > 0, "The price should be between "+ toNuls(afterPrice) + " and " + toNuls(beforePrice));
            domainPrice.put(length, price);
        }
        emit(new ChangeDomainPrice(length, price.toString()));
    }

    public void changeDefaultDomainPrice(BigInteger price) {
        onlyOwner();
        require(price.compareTo(treasuryManager.ONE_NULS) >= 0, "Error price");

        BigInteger _price = domainPrice.get(defaultPriceLength - 1);
        require(_price.compareTo(price) > 0, "The price is high");
        this.defaultPrice = price;
        emit(new ChangeDefaultDomainPrice(defaultPriceLength, price.toString()));
    }

    @Payable
    public boolean mint(@Required String domain) {
        _nonReentrantBefore();
        boolean bool = this._mintWithTokenURI(Msg.sender(), domain, null, true);
        _nonReentrantAfter();
        return bool;
    }

    @Payable
    public boolean mintWithTokenURI(@Required String domain, @Required String tokenURI) {
        _nonReentrantBefore();
        boolean bool = this._mintWithTokenURI(Msg.sender(), domain, tokenURI, true);
        _nonReentrantAfter();
        return bool;
    }

    @Payable
    public void activeAward(String domain) {
        _nonReentrantBefore();
        BigInteger tokenId = domainIndexes.get(domain);
        require(tokenId != null, "Not exist domain");
        Boolean award = domainAwards.get(domain);
        require(award == null || !award, "Already active");
        Address token721 = this.get721ById(tokenId);
        NRC721 nrc721 = new NRC721(token721);
        require(nrc721.ownerOf(tokenId).equals(Msg.sender()), "NRC721: token that is not own");
        String[] split = domain.split("\\.");
        String suffix = split[split.length - 1];
        /*String prefix = "";
        for (int i = 0, length = split.length - 1; i < length; i ++) {
            prefix += split[i];
            if (i != length - 1) {
                prefix += ".";
            }
        }*/
        this._activeAward(Msg.sender(), Msg.value(), suffix, domain, false);
        _nonReentrantAfter();
    }

    @Payable
    public void changeMainDomain(String domain) {
        _nonReentrantBefore();
        require(Msg.value().compareTo(treasuryManager.ONE_NULS.multiply(BigInteger.TEN)) >= 0, "10 NULS cost");
        BigInteger tokenId = domainIndexes.get(domain);
        require(tokenId != null, "Not exist domain");
        Address token721 = this.get721ById(tokenId);
        NRC721 nrc721 = new NRC721(token721);
        require(nrc721.ownerOf(tokenId).equals(Msg.sender()), "NRC721: token that is not own");
        UserInfo userInfo = userDomains.get(Msg.sender());
        userInfo.setMainDomain(domain);
        treasuryManager.getTreasury().transfer(Msg.value());
        _nonReentrantAfter();
    }

    public void receiveAward() {
        _nonReentrantBefore();
        Address user = Msg.sender();
        UserInfo userInfo = userDomains.get(user);
        require(userInfo != null && userInfo.getActiveDomainsSize() > 0, "No domains");
        updatePool();
        _receive(user, userInfo);
        userInfo.setRewardDebt(BigInteger.valueOf(userInfo.getActiveDomainsSize()).multiply(accPerShare).divide(_1e12));
        _nonReentrantAfter();
    }

    /*public void burn(@Required Address owner, @Required BigInteger tokenId) {
        require(isApprovedOrOwner(Msg.sender(), tokenId), "NRC721: transfer caller is not owner nor approved");
        super.burnBase(owner, tokenId);
    }*/

    public boolean batchMint(@Required String[] tos, @Required String[] domains) {
        onlyOwner();
        require(tos.length <= 100, "max size: 100.");
        require(tos.length == domains.length, "array size error.");
        for (int i = 0; i < tos.length; i++) {
            this._mintWithTokenURI(new Address(tos[i]), domains[i], null, false);
        }
        return true;
    }

    public boolean batchMintWithTokenURI(@Required String[] tos, @Required String[] domains, @Required String[] tokenURIs) {
        onlyOwner();
        require(tos.length <= 100, "max size: 100.");
        require(tos.length == domains.length && domains.length == tokenURIs.length, "array size error.");
        for (int i = 0; i < tos.length; i++) {
            this._mintWithTokenURI(new Address(tos[i]), domains[i], tokenURIs[i], false);
        }
        return true;
    }

    public void setTokenURI(BigInteger tokenId, String uri) {
        NRC721 nrc721 = new NRC721(this.get721ById(tokenId));
        require(nrc721.ownerOf(tokenId).equals(Msg.sender()), "NRC721: token that is not own");
        nrc721.setTokenURI(tokenId, uri);
    }

    public void domainTransfer(Address from, Address to, BigInteger tokenId) {
        onlyDomain721();
        UserInfo userFrom = userDomains.get(from);
        String domain = domains.get(tokenId);
        require(domain != null, "Domain Get: error tokenId");
        Boolean award = domainAwards.get(domain);
        boolean active = award != null && award;
        if (active) {
            require(userFrom.existActive(domain), "Domain Active Check: error domain");
        } else {
            require(userFrom.existInactive(domain), "Domain Inactive Check: error domain");
        }
        updatePool();
        UserInfo userTo = userDomains.get(to);
        if (userTo == null) {
            userTo = new UserInfo();
            userDomains.put(to, userTo);
        }
        _receive(from, userFrom);
        _receive(to, userTo);

        if (!active) {
            userTo.addInactiveDomains(domain);
            userFrom.removeInactiveDomains(domain);
        } else {
            userTo.addActiveDomains(domain);
            userFrom.removeActiveDomains(domain);
        }
        userFrom.setRewardDebt(BigInteger.valueOf(userFrom.getActiveDomainsSize()).multiply(accPerShare).divide(_1e12));
        userTo.setRewardDebt(BigInteger.valueOf(userTo.getActiveDomainsSize()).multiply(accPerShare).divide(_1e12));
        emit(new DomainTransfer(from, to, domain));
    }

    @View
    public String getDefaultPrice() {
        return defaultPrice.toString();
    }

    @View
    public int getDefaultPriceLength() {
        return defaultPriceLength;
    }

    @View
    public String getDomainPrice(int length) {
        BigInteger price = domainPrice.get(length);
        return price != null ? price.toString() : "0";
    }

    @View
    public String getPriceByDomain(String domain) {
        String[] split = domain.split("\\.");
        String suffix = split[split.length - 1];
        Address token721 = domainSuffixFor721Map.get(suffix);
        require(token721 != null, "check 721: error domain");
        BigInteger price = this.getPrice(suffix, domain);
        return price.toString();
    }

    @View
    public String getStakingAddress() {
        return treasuryManager.getStaking().toString();
    }

    @JSONSerializable
    @View
    public UserInfo userDomains(@Required Address user) {
        return userDomains.get(user);
    }

    @View
    public String domain(@Required BigInteger tokenId) {
        return domains.get(tokenId);
    }

    @View
    public String domainId(@Required String domain) {
        BigInteger id = domainIndexes.get(domain);
        return id != null ? id.toString() : "";
    }

    @View
    public boolean isActiveAward(@Required String domain) {
        Boolean active = domainAwards.get(domain);
        if (active == null) {
            return false;
        }
        return active;
    }

    @View
    public String getAccPerShare() {
        return accPerShare.toString();
    }

    @View
    public String getLastAward() {
        return lastAward.toString();
    }

    @JSONSerializable
    @View
    public Map<String, NextId> getStartIds() {
        return domainNextIds;
    }

    @JSONSerializable
    @View
    public Map<String, Address> getDomainSuffixes() {
        return domainSuffixFor721Map;
    }

    @View
    public String getRewardCount() {
        return rewardCount.toString();
    }

    private void addSuffixInfo(String suffix, Address _721, BigInteger startId) {
        require(!domainSuffixFor721Map.containsKey(suffix), "Add Suffix: already exist");
        domainSuffixFor721Map.put(suffix, _721);
        token721ForSuffixMap.put(_721, suffix);
        domainNextIds.put(suffix, new NextId(startId));
        token721ByStartIds.put(startId, _721);
    }

    @View
    public String tokenURI(String domain) {
        BigInteger id = domainIndexes.get(domain);
        require(id != null, "error domain");
        Address address = this.get721ById(id);
        NRC721 nrc721 = new NRC721(address);
        return nrc721.tokenURI(id);
    }

    @View
    public Address get721ById(BigInteger tokenId) {
        BigInteger key = tokenId.divide(_100000).multiply(_100000);
        Address token721 = token721ByStartIds.get(key);
        require(token721 != null, "error tokenId");
        return token721;
    }

    @View
    public String pendingAward(Address user) {
        UserInfo userInfo = userDomains.get(user);
        require(userInfo != null && userInfo.getActiveDomainsSize() > 0, "No domains");
        Staking staking = new Staking(treasuryManager.getStaking());
        BigInteger totalAward = staking.ownerTotalConsensusAward();
        BigInteger award = totalAward.subtract(lastAward);
        accPerShare = accPerShare.add(award.multiply(_1e12).divide(rewardCount));
        BigInteger pending = BigInteger.valueOf(userInfo.getActiveDomainsSize()).multiply(accPerShare).divide(_1e12).subtract(userInfo.getRewardDebt());
        pending = pending.add(userInfo.getPending());
        return pending.toString();
    }

    @View
    public String getPendingStakingAmount() {
        return treasuryManager.getAvailable().toString();
    }

    @View
    public String getStakingAmount() {
        return treasuryManager.getStakingAmount().toString();
    }

    @View
    public String getWholeStakingAmount() {
        return treasuryManager.getTotal().toString();
    }

    private void updatePool() {
        Staking staking = new Staking(treasuryManager.getStaking());
        BigInteger totalAward = staking.ownerTotalConsensusAward();
        if (totalAward.compareTo(BigInteger.ZERO) == 0) {
            return;
        }
        BigInteger availableAward = staking.ownerAvailableConsensusAward();
        if (availableAward.compareTo(treasuryManager.MININUM_TRANSFER_AMOUNT) >= 0) {
            staking.transferConsensusRewardByOwner();
        }
        BigInteger award = totalAward.subtract(lastAward);
        accPerShare = accPerShare.add(award.multiply(_1e12).divide(rewardCount));
        lastAward = totalAward;
    }

    private BigInteger _receive(Address user, UserInfo userInfo) {
        int count = userInfo.getActiveDomainsSize();
        if (count == 0) {
            return BigInteger.ZERO;
        }
        BigInteger pending = BigInteger.valueOf(count).multiply(accPerShare).divide(_1e12).subtract(userInfo.getRewardDebt());
        pending = pending.add(userInfo.getPending());
        if (pending.compareTo(BigInteger.ZERO) == 0) {
            return BigInteger.ZERO;
        }
        if (pending.compareTo(treasuryManager.MININUM_TRANSFER_AMOUNT) < 0) {
            userInfo.setPending(pending);
            emit(new UserPendingAward(user.toString(), pending));
            return BigInteger.ZERO;
        }
        userInfo.setPending(BigInteger.ZERO);
        user.transfer(pending);
        return pending;
    }

    private BigInteger extractDecimal(BigInteger na) {
        return na.subtract(toNuls(na).toBigInteger().multiply(treasuryManager.ONE_NULS));
    }

    private BigDecimal toNuls(BigInteger na) {
        return new BigDecimal(na).movePointLeft(8);
    }

    private void _activeAward(Address user, BigInteger userPay, String suffix, String domain, boolean newId) {
        BigInteger price = this.getPrice(suffix, domain);
        require(userPay.compareTo(price) >= 0, "Insufficient payment");
        BigInteger decimalValue = this.extractDecimal(userPay);
        boolean hasDecimal = decimalValue.compareTo(BigInteger.ZERO) > 0;
        require(!hasDecimal, "Domain mint: payment not good, floating point numbers are not allowed");

        UserInfo userInfo = userDomains.get(user);
        if (userInfo == null) {
            userInfo = new UserInfo();
            userDomains.put(user, userInfo);
        }
        updatePool();
        _receive(user, userInfo);
        if (!newId) {
            userInfo.removeInactiveDomains(domain);
        }
        userInfo.addActiveDomains(domain);
        rewardCount = rewardCount.add(BigInteger.ONE);
        userInfo.setRewardDebt(BigInteger.valueOf(userInfo.getActiveDomainsSize()).multiply(accPerShare).divide(_1e12));
        domainAwards.put(domain, true);
        treasuryManager.add(userPay);
        emit(new UserActiveAward(user.toString(), userPay, domain, newId));
    }

    private boolean _mintWithTokenURI(Address to, String domain, String tokenURI, boolean reward) {
        require(!domainIndexes.containsKey(domain), "Already exist domain");
        String[] split = domain.split("\\.");
        String suffix = split[split.length - 1];
        Address token721 = domainSuffixFor721Map.get(suffix);
        require(token721 != null, "check 721: error domain");
        NextId nextId = domainNextIds.get(suffix);
        require(nextId != null, "check nextId: error domain");
        UserInfo userInfo = userDomains.get(to);
        if (userInfo == null) {
            userInfo = new UserInfo();
            userDomains.put(to, userInfo);
        }
        if (reward) {
            /*String prefix = "";
            for (int i = 0, length = split.length - 1; i < length; i ++) {
                prefix += split[i];
                if (i != length - 1) {
                    prefix += ".";
                }
            }*/
            this._activeAward(to, Msg.value(), suffix, domain, true);
        } else {
            userInfo.addInactiveDomains(domain);
        }
        BigInteger tokenId = nextId.getIdAndAddOne();
        NRC721 nrc721 = new NRC721(token721);
        nrc721.mintWithTokenURI(to, tokenId, tokenURI);
        domains.put(tokenId, domain);
        domainIndexes.put(domain, tokenId);
        return true;
    }

    private BigInteger getPrice(String suffix, String domain) {
        require(domain != null && suffix != null, "Error domain");
        int prefixLength = domain.length() - (suffix.length() + 1);
        require(prefixLength > 0 && prefixLength <= 64, "Error domain length");
        BigInteger price = domainPrice.get(prefixLength);
        if (price == null) {
            price = defaultPrice;
        }
        return price;
    }



}
