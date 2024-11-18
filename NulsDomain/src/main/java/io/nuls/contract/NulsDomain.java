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
import io.nuls.contract.event.ChangeDomainPrice;
import io.nuls.contract.event.UserActiveAward;
import io.nuls.contract.event.UserPendingAward;
import io.nuls.contract.manager.TreasuryManager;
import io.nuls.contract.model.NextId;
import io.nuls.contract.model.UserInfo;
import io.nuls.contract.role.Ownable;
import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Contract;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.JSONSerializable;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static io.nuls.contract.sdk.Utils.*;

/**
 * @author: PierreLuo
 * @date: 2019-06-10
 */
public class NulsDomain extends Ownable implements Contract {

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
    private final BigInteger DEFAULT_PRICE;
    private final TreasuryManager treasuryManager;
    private BigInteger accPerShare = BigInteger.ZERO;
    private BigInteger lastAward = BigInteger.ZERO;
    private final BigInteger _1e12 = BigInteger.TEN.pow(12);
    private boolean initialized = false;
    private BigInteger lastDefaultStartId;
    private final BigInteger _100000 = BigInteger.valueOf(100000);

    public NulsDomain() {
        this.treasuryManager = new TreasuryManager();
        domainPrice.put(2, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(1000)));
        domainPrice.put(3, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(500)));
        domainPrice.put(4, treasuryManager.ONE_NULS.multiply(BigInteger.valueOf(100)));
        this.DEFAULT_PRICE = treasuryManager.ONE_NULS;
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
        require(length > 0, "error length");
        require(price.compareTo(treasuryManager.ONE_NULS) >= 0, "Error price");
        domainPrice.put(length, price);
        emit(new ChangeDomainPrice(length, price.toString()));
    }

    @Payable
    public boolean mint(@Required String domain) {
        return this._mintWithTokenURI(Msg.sender(), domain, null, true);
    }

    @Payable
    public boolean mintWithTokenURI(@Required String domain, @Required String tokenURI) {
        return this._mintWithTokenURI(Msg.sender(), domain, tokenURI, true);
    }

    @Payable
    public void activeAward(String domain) {
        BigInteger tokenId = domainIndexes.get(domain);
        require(tokenId != null, "Not exist domain");
        Boolean award = domainAwards.get(domain);
        require(award == null || !award, "Already active");
        Address token721 = this.get721ById(tokenId);
        NRC721 nrc721 = new NRC721(token721);
        require(nrc721.ownerOf(tokenId).equals(Msg.sender()), "NRC721: token that is not own");
        this._activeAward(Msg.sender(), Msg.value(), domain, false);
    }

    @Payable
    public void changeMainDomain(String domain) {
        require(Msg.value().compareTo(treasuryManager.ONE_NULS.multiply(BigInteger.TEN)) >= 0, "10 NULS cost");
        BigInteger tokenId = domainIndexes.get(domain);
        require(tokenId != null, "Not exist domain");
        Address token721 = this.get721ById(tokenId);
        NRC721 nrc721 = new NRC721(token721);
        require(nrc721.ownerOf(tokenId).equals(Msg.sender()), "NRC721: token that is not own");
        UserInfo userInfo = userDomains.get(Msg.sender());
        userInfo.setMainDomain(domain);
        treasuryManager.getTreasury().transfer(Msg.value());
    }

    public void receiveAward() {
        Address user = Msg.sender();
        UserInfo userInfo = userDomains.get(user);
        require(userInfo != null && userInfo.getActiveDomainsSize() > 0, "No domains");
        updatePool();
        _receive(user, userInfo);
        userInfo.setRewardDebt(BigInteger.valueOf(userInfo.getActiveDomainsSize()).multiply(accPerShare).divide(_1e12));
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
    }

    @View
    public String getDomainPrice(int length) {
        BigInteger price = domainPrice.get(length);
        return price != null ? price.toString() : "0";
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
        domainSuffixFor721Map.put(suffix, _721);
        token721ForSuffixMap.put(_721, suffix);
        domainNextIds.put(suffix, new NextId(startId));
        token721ByStartIds.put(startId, _721);
    }

    @View
    public Address get721ById(BigInteger tokenId) {
        BigInteger key = tokenId.divide(_100000).multiply(_100000);
        Address token721 = token721ByStartIds.get(key);
        require(token721 != null, "error tokenId");
        return token721;
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
        if (pending.compareTo(treasuryManager.MININUM_TRANSFER_AMOUNT) < 0) {
            userInfo.setPending(pending);
            emit(new UserPendingAward(user.toString(), pending));
            return BigInteger.ZERO;
        }
        userInfo.setPending(BigInteger.ZERO);
        user.transfer(pending);
        return pending;
    }

    private void _activeAward(Address user, BigInteger userPay, String domain, boolean newId) {
        BigInteger price = this.getPrice(domain);
        require(userPay.compareTo(price) >= 0, "Insufficient payment");
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
            this._activeAward(to, Msg.value(), domain, true);
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

    private BigInteger getPrice(String domain) {
        require(domain != null && domain.length() > 1, "Error domain length");
        BigInteger price = domainPrice.get(domain.length());
        if (price == null) {
            price = DEFAULT_PRICE;
        }
        return price;
    }



}
