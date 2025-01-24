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
import io.nuls.contract.model.DomainPrice;
import io.nuls.contract.model.NextId;
import io.nuls.contract.model.UserInfo;
import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Contract;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.Utils;
import io.nuls.contract.sdk.annotation.JSONSerializable;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;
import io.nuls.contract.sdk.event.DebugEvent;
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
    private final Map<String, DomainPrice> domainPriceMap = new HashMap<String, DomainPrice>();

    private final TreasuryManager treasuryManager;
    private BigInteger accPerShare = BigInteger.ZERO;
    private BigInteger lastAward = BigInteger.ZERO;
    private final BigInteger _1e12 = BigInteger.TEN.pow(12);
    private boolean initialized = false;
    private BigInteger lastDefaultStartId;
    private final BigInteger _100000 = BigInteger.valueOf(100000);

    public NulsDomain() {
        this.treasuryManager = new TreasuryManager();
    }

    public void initialize(
            Address staking,
            Address treasury,
            Address official,
            Address nulsSuffix721) {
        onlyOwner();
        if (initialized) {
            revert("DomainInitialized");
        }
        initialized = true;
        super.setOfficial(official);
        treasuryManager.setStaking(staking);
        treasuryManager.setTreasury(treasury);
        lastDefaultStartId = BigInteger.ZERO;
        this.addSuffixInfo("ai", nulsSuffix721, lastDefaultStartId);
    }

    protected void onlyDomain721() {
        require(token721ForSuffixMap.containsKey(Msg.sender()), "Only domain721 can call it.");
    }

    protected void checkPub(String pub) {
        require(Utils.getAddressByPublicKey(pub).equals(Msg.sender().toString()), "Error pubKey");
    }

    public void setFeeRate(int feeRate) {
        onlyOfficial();
        require(feeRate >= 10 && feeRate < 100, "error feeRate");
        treasuryManager.setFeeRate(BigInteger.valueOf(feeRate));
    }

    public void setTreasury(Address treasury) {
        onlyOfficial();
        treasuryManager.setTreasury(treasury);
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

    public void changeDomainPrice(String suffix, int length, BigInteger price) {
        onlyOfficial();
        require(length > 0 && length <= 64, "error length");
        require(price.compareTo(treasuryManager.ONE_NULS) >= 0, "Error price");
        DomainPrice domainPrice = domainPriceMap.get(suffix);
        require(domainPrice != null, "Domain Suffix Not Exist");

        BigInteger prePrice = BigInteger.ZERO;
        int defaultPriceLength = domainPrice.getDefaultPriceLength();
        BigInteger defaultPrice = domainPrice.getDefaultPrice();
        Map<Integer, BigInteger> priceMap = domainPrice.getDomainPrice();
        if (length >= defaultPriceLength) {
            require(length == defaultPriceLength, "Can only be added in order");
            require(price.compareTo(defaultPrice) > 0, "Price must be greater than the default price");
            defaultPriceLength++;
            domainPrice.setDefaultPriceLength(defaultPriceLength);
            priceMap.put(length, price);
        } else {
            prePrice = priceMap.get(length);
            int before = length - 1;
            int after = length + 1;
            BigInteger beforePrice;
            BigInteger afterPrice;
            if (before == 0) {
                beforePrice = treasuryManager.ONE_NULS.multiply(treasuryManager.ONE_NULS);
            } else {
                beforePrice = priceMap.get(before);
            }
            if (after == defaultPriceLength) {
                afterPrice = defaultPrice;
            } else {
                afterPrice = priceMap.get(after);
            }
            require(price.compareTo(beforePrice) < 0 && price.compareTo(afterPrice) > 0, "The price should be between "+ toNuls(afterPrice) + " and " + toNuls(beforePrice));
            priceMap.put(length, price);
        }
        emit(new ChangeDomainPrice(suffix, length, prePrice.toString(), price.toString()));
    }

    public void changeDefaultDomainPrice(String suffix, BigInteger price) {
        onlyOfficial();
        require(price.compareTo(treasuryManager.ONE_NULS) >= 0, "Error price");
        DomainPrice domainPrice = domainPriceMap.get(suffix);
        require(domainPrice != null, "Domain Suffix Not Exist");

        int defaultPriceLength = domainPrice.getDefaultPriceLength();
        Map<Integer, BigInteger> priceMap = domainPrice.getDomainPrice();
        BigInteger _price = priceMap.get(defaultPriceLength - 1);
        require(_price.compareTo(price) > 0, "The price is high");
        BigInteger defaultPrice = domainPrice.getDefaultPrice();
        domainPrice.setDefaultPrice(price);
        emit(new ChangeDefaultDomainPrice(suffix, defaultPriceLength, defaultPrice.toString(), price.toString()));
    }

    public void changeStaking(Address _staking) {
        onlyOfficial();
        treasuryManager.setStaking(_staking);
    }

    @Payable
    public boolean mint(@Required String domain, @Required String pub) {
        _nonReentrantBefore();
        this.checkPub(pub);
        boolean bool = this._mintWithTokenURI(Msg.sender(), domain, null, true, pub);
        _nonReentrantAfter();
        return bool;
    }

    @Payable
    public boolean mintWithTokenURI(@Required String domain, @Required String tokenURI, @Required String pub) {
        _nonReentrantBefore();
        this.checkPub(pub);
        boolean bool = this._mintWithTokenURI(Msg.sender(), domain, tokenURI, true, pub);
        _nonReentrantAfter();
        return bool;
    }

    public boolean mintHistory(@Required String domain, @Required String pub) {
        _nonReentrantBefore();
        this.checkPub(pub);
        UserInfo userInfo = this.checkUserHistory();
        boolean bool = this._mintWithTokenURI(Msg.sender(), domain, null, false, pub);
        userInfo.setHistoryQuota(userInfo.getHistoryQuota() - 1);
        _nonReentrantAfter();
        return bool;
    }

    @Payable
    public void activeAward(@Required String domain, @Required String pub) {
        _nonReentrantBefore();
        this.checkPub(pub);
        BigInteger tokenId = domainIndexes.get(domain);
        require(tokenId != null, "Not exist domain");
        Boolean award = domainAwards.get(domain);
        require(award == null || !award, "Already active");
        String token721 = this.get721ById(tokenId);
        require(!token721.isEmpty(), "Error tokenId");
        NRC721 nrc721 = new NRC721(new Address(token721));
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
        this._activeAward(Msg.sender(), Msg.value(), suffix, domain, false, pub);
        _nonReentrantAfter();
    }

    public void changeMainDomain(@Required String domain, @Required String pub) {
        _nonReentrantBefore();
        this.checkPub(pub);
        BigInteger tokenId = domainIndexes.get(domain);
        require(tokenId != null, "Not exist domain");
        String token721 = this.get721ById(tokenId);
        require(!token721.isEmpty(), "Error tokenId");
        NRC721 nrc721 = new NRC721(new Address(token721));
        require(nrc721.ownerOf(tokenId).equals(Msg.sender()), "NRC721: token that is not own");
        UserInfo userInfo = userDomains.get(Msg.sender());
        userInfo.updatePub(pub);
        userInfo.setMainDomain(domain);
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

    public boolean batchUpdatePub(@Required String[] tos, @Required String[] pubs) {
        onlyOwner();
        require(tos.length <= 100, "max size: 100.");
        require(tos.length == pubs.length, "array size error.");
        String to;
        String pub;
        for (int i = 0; i < tos.length; i++) {
            to = tos[i];
            UserInfo userInfo = this.userDomains(new Address(to));
            require(userInfo != null, "Error User: " + to);
            pub = pubs[i];
            require(pub != null && !pub.isEmpty() && Utils.getAddressByPublicKey(pub).equals(to), "Error pubKey: " + to);
            userInfo.updatePub(pub);
        }
        return true;
    }

    public boolean batchMintHistoryQuota(@Required String[] tos, @Required int[] quota) {
        onlyOfficial();
        require(tos.length <= 100, "max size: 100.");
        require(tos.length == quota.length, "array size error.");
        for (int i = 0; i < tos.length; i++) {
            Address toAddr = new Address(tos[i]);
            UserInfo userInfo = userDomains.get(toAddr);
            if (userInfo == null) {
                userInfo = new UserInfo();
                userDomains.put(toAddr, userInfo);
            }
            userInfo.setHistoryQuota(quota[i]);
        }
        return true;
    }

    public boolean batchMint(@Required String[] tos, @Required String[] domains, @Required String[] pubs) {
        onlyOfficial();
        require(tos.length <= 100, "max size: 100.");
        require(tos.length == domains.length && domains.length == pubs.length, "array size error.");
        String to;
        String pub;
        for (int i = 0; i < tos.length; i++) {
            to = tos[i];
            pub = pubs[i];
            if (pub != null && !pub.isEmpty()) {
                require(Utils.getAddressByPublicKey(pub).equals(to), "Error pubKey: " + to);
            }
            this._mintWithTokenURI(new Address(to), domains[i], null, false, pub);
        }
        return true;
    }

    public boolean batchMintWithTokenURI(@Required String[] tos, @Required String[] domains, @Required String[] tokenURIs, @Required String[] pubs) {
        onlyOfficial();
        require(tos.length <= 100, "max size: 100.");
        require(tos.length == domains.length && domains.length == tokenURIs.length && tokenURIs.length == pubs.length, "array size error.");
        String to;
        String pub;
        for (int i = 0; i < tos.length; i++) {
            to = tos[i];
            pub = pubs[i];
            if (pub != null && !pub.isEmpty()) {
                require(Utils.getAddressByPublicKey(pub).equals(to), "Error pubKey: " + to);
            }
            this._mintWithTokenURI(new Address(to), domains[i], tokenURIs[i], false, pub);
        }
        return true;
    }

    public void setTokenURI(BigInteger tokenId, String uri) {
        String token721 = this.get721ById(tokenId);
        require(!token721.isEmpty(), "Error tokenId");
        NRC721 nrc721 = new NRC721(new Address(token721));
        require(nrc721.ownerOf(tokenId).equals(Msg.sender()), "NRC721: token that is not own");
        nrc721.setTokenURI(tokenId, uri);
    }

    public void setUserURI(String uri) {
        UserInfo userInfo = userDomains.get(Msg.sender());
        require(userInfo != null, "not exist");
        require(uri != null, "uri error");
        userInfo.setUri(uri);
    }

    public void domainTransfer(Address from, Address to, BigInteger tokenId) {
        onlyDomain721();
        String token721 = this.get721ById(tokenId);
        require(!token721.isEmpty(), "Domain transfer: error tokenId");
        require(Msg.sender().equals(new Address(token721)), "Domain transfer: token721 caller error");
        UserInfo userFrom = userDomains.get(from);
        String domain = domains.get(tokenId);
        require(domain != null, "Domain Get: error tokenId");
        updatePool();
        if (from.equals(to)) {
            _receive(from, userFrom);
            userFrom.setRewardDebt(BigInteger.valueOf(userFrom.getActiveDomainsSize()).multiply(accPerShare).divide(_1e12));
        } else {
            Boolean award = domainAwards.get(domain);
            boolean active = award != null && award;
            if (active) {
                require(userFrom.existActive(domain), "Domain Active Check: error domain");
            } else {
                require(userFrom.existInactive(domain), "Domain Inactive Check: error domain");
            }
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
        }
        emit(new DomainTransfer(from, to, domain));
    }

    @View
    public int getFeeRate() {
        return treasuryManager.getFeeRate().intValue();
    }

    @View
    public String getDefaultPrice(String suffix) {
        DomainPrice domainPrice = domainPriceMap.get(suffix);
        if (domainPrice == null) {
            return "0";
        }
        return domainPrice.getDefaultPrice().toString();
    }

    @View
    public int getDefaultPriceLength(String suffix) {
        DomainPrice domainPrice = domainPriceMap.get(suffix);
        if (domainPrice == null) {
            return 0;
        }
        return domainPrice.getDefaultPriceLength();
    }

    @View
    public String getDomainPrice(String suffix, int length) {
        DomainPrice domainPrice = domainPriceMap.get(suffix);
        if (domainPrice == null) {
            return "0";
        }
        BigInteger price = domainPrice.getDomainPrice().get(length);
        return price != null ? price.toString() : domainPrice.getDefaultPrice().toString();
    }

    @JSONSerializable
    @View
    public String[] getPriceByDomain(String domain) {
        String[] split = domain.split("\\.");
        String suffix = split[split.length - 1];
        Address token721 = domainSuffixFor721Map.get(suffix);
        require(token721 != null, "check 721: error domain");
        BigInteger price = this.getPrice(suffix, domain);
        return new String[]{price.toString(), this.isActiveAward(domain) + ""};
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
    public int userHistoryQuota(@Required Address user) {
        UserInfo userInfo = userDomains.get(user);
        if (userInfo == null) {
            return 0;
        }
        return userInfo.getHistoryQuota();
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
        domainPriceMap.put(suffix, new DomainPrice(treasuryManager.ONE_NULS));
    }

    @View
    public String tokenURI(String domain) {
        BigInteger id = domainIndexes.get(domain);
        if (id == null) {
            return "";
        }
        String address = this.get721ById(id);
        if (address.isEmpty()) {
            return "";
        }
        NRC721 nrc721 = new NRC721(new Address(address));
        return nrc721.tokenURI(id);
    }

    @JSONSerializable
    @View
    public String[] userAddress(String domain) {
        BigInteger id = domainIndexes.get(domain);
        if (id == null) {
            return new String[]{"", ""};
        }
        String address = this.get721ById(id);
        if (address.isEmpty()) {
            return new String[]{"", ""};
        }
        NRC721 nrc721 = new NRC721(new Address(address));
        Address owner = nrc721.ownerOf(id);
        if (owner == null) {
            return new String[]{"", ""};
        }
        UserInfo userInfo = userDomains.get(owner);
        if (userInfo == null) {
            return new String[]{"", ""};
        }
        return new String[]{owner.toString(), userInfo.getPub() == null ? "" : userInfo.getPub()};
    }

    @View
    public String userURI(Address user) {
        UserInfo userInfo = userDomains.get(user);
        if (userInfo == null) {
            return "";
        }
        return userInfo.getUri();
    }

    @View
    public String getUserRewardReceived(Address user) {
        UserInfo userInfo = userDomains.get(user);
        if (userInfo == null) {
            return "0";
        }
        return userInfo.getReceived().toString();
    }

    @View
    public String get721ById(BigInteger tokenId) {
        BigInteger key = tokenId.divide(_100000).multiply(_100000);
        Address token721 = token721ByStartIds.get(key);
        return token721 == null ? "" : token721.toString();
    }

    @View
    public String pendingAward(Address user) {
        UserInfo userInfo = userDomains.get(user);
        if (userInfo == null || userInfo.getActiveDomainsSize() == 0) {
            return "0";
        }
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
        } else {
            emit(new DebugEvent("updatePool", "availableAward: " + availableAward + ", totalAward: " + totalAward + ", lastAward: " + lastAward + ", rewardCount: " + rewardCount));
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
        BigInteger prePending = userInfo.getPending();
        BigInteger pending = BigInteger.valueOf(count).multiply(accPerShare).divide(_1e12).subtract(userInfo.getRewardDebt());
        pending = pending.add(prePending);
        if (pending.compareTo(BigInteger.ZERO) == 0) {
            return BigInteger.ZERO;
        }
        BigInteger balance = Msg.address().balance();
        if (pending.compareTo(treasuryManager.MININUM_TRANSFER_AMOUNT) < 0 || pending.compareTo(balance) > 0) {
            userInfo.setPending(pending);
            emit(new UserPendingAward(user.toString(), prePending, pending, balance));
            return BigInteger.ZERO;
        }
        userInfo.setPending(BigInteger.ZERO);
        userInfo.addReceived(pending);
        user.transfer(pending);
        return pending;
    }

    private BigInteger extractDecimal(BigInteger na) {
        return na.subtract(toNuls(na).toBigInteger().multiply(treasuryManager.ONE_NULS));
    }

    private BigDecimal toNuls(BigInteger na) {
        return new BigDecimal(na).movePointLeft(8);
    }

    private void _activeAward(Address user, BigInteger userPay, String suffix, String domain, boolean newId, String pub) {
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
        userInfo.updatePub(pub);
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

    private boolean _mintWithTokenURI(Address to, String domain, String tokenURI, boolean reward, String pub) {
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
        userInfo.updatePub(pub);
        if (reward) {
            /*String prefix = "";
            for (int i = 0, length = split.length - 1; i < length; i ++) {
                prefix += split[i];
                if (i != length - 1) {
                    prefix += ".";
                }
            }*/
            this._activeAward(to, Msg.value(), suffix, domain, true, null);
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
        DomainPrice domainPrice = domainPriceMap.get(suffix);
        require(domainPrice != null, "Domain Suffix Not Exist");
        Map<Integer, BigInteger> priceMap = domainPrice.getDomainPrice();
        int prefixLength = domain.length() - (suffix.length() + 1);
        require(prefixLength > 0 && prefixLength <= 64, "Error domain length");
        BigInteger price = priceMap.get(prefixLength);
        if (price == null) {
            price = domainPrice.getDefaultPrice();
        }
        return price;
    }

    private UserInfo checkUserHistory() {
        Address sender = Msg.sender();
        UserInfo userInfo = userDomains.get(sender);
        require(userInfo != null && userInfo.getHistoryQuota() > 0, "error user history quota");
        return userInfo;
    }

}
