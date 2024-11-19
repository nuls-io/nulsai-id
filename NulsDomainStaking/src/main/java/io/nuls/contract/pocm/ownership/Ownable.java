package io.nuls.contract.pocm.ownership;

import io.nuls.contract.pocm.manager.PocmInfo;
import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Event;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.Payable;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;
import io.nuls.contract.sdk.token.AssetWrapper;
import io.nuls.contract.sdk.token.NRC20Wrapper;
import io.nuls.contract.sdk.token.Token;

import java.math.BigInteger;

import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;

/**
 * @author: Long
 * @date: 2019-03-15
 */
public class Ownable {

    /**
     * 合约创建者
     */
    protected Address contractCreator;

    protected Address owner;
    protected Address awardReceiver;

    protected String OFFICIAL_ADDRESS;
    protected Address LP_ADDRESS;

    private PocmInfo pi;

    public Ownable() {
        this.owner = Msg.sender();
        this.contractCreator = this.owner;
        if (this.owner.toString().startsWith("NULS")) {
            LP_ADDRESS = new Address("NULSd6HgtaXPXjkvegxYZHpzwwmieEgx7kUCc");
        } else {
            //OFFICIAL_ADDRESS = "tNULSeBaMuU6sq72mptyghDXDWQXKJ5QUaWhGj";
            LP_ADDRESS = new Address("tNULSeBaNA4tPMPCFaBLV1cfZUzNTWn934Qx73");
        }
    }

    protected void setOfficial(String officialAddress) {
        this.OFFICIAL_ADDRESS = officialAddress;
    }

    protected void setAwardReceiver(Address awardReceiver) {
        this.awardReceiver = awardReceiver;
    }

    @View
    public Address getAwardReceiver() {
        return awardReceiver;
    }

    @View
    public Address viewOwner() {
        return owner;
    }

    @View
    public String viewOfficial() {
        return OFFICIAL_ADDRESS;
    }

    @View
    public Address viewLp() {
        return LP_ADDRESS;
    }

    @View
    public String viewContractCreator() {
        return this.contractCreator != null ? this.contractCreator.toString() : "";
    }

    protected void onlyOwner() {
        require(Msg.sender().equals(owner), "Only the owner of the contract can execute it.");
    }

    protected void onlyOwnerOrOfficial() {
        require(Msg.sender().equals(owner) || Msg.sender().toString().equals(OFFICIAL_ADDRESS), "Refused.");
    }

    protected void onlyOfficial() {
        require(Msg.sender().toString().equals(OFFICIAL_ADDRESS), "Refused.");
    }

    /**
     * 转让合约所有权
     *
     * @param newOwner
     */
    public void transferOwnership(Address newOwner) {
        onlyOwnerOrOfficial();
        require(newOwner != null, "Empty new owner");
        emit(new OwnershipTransferredEvent(owner, newOwner));
        owner = newOwner;
    }

    public void transferOfficialShip(Address newOfficial) {
        onlyOfficial();
        require(newOfficial != null, "Empty new official");
        emit(new OwnershipTransferredEvent(new Address(OFFICIAL_ADDRESS), newOfficial));
        OFFICIAL_ADDRESS = newOfficial.toString();
    }


    @Payable
    public void repairBalance() {
        onlyOfficial();
    }

    public void transferOtherNRC20(@Required Address nrc20, @Required Address to, @Required BigInteger value) {
        onlyOfficial();
        require(!Msg.address().equals(nrc20), "Do nothing by yourself");
        require(nrc20.isContract(), "[" + nrc20.toString() + "] is not a contract address");
        NRC20Wrapper wrapper = new NRC20Wrapper(nrc20);
        BigInteger balance = wrapper.balanceOf(Msg.address());
        require(balance.compareTo(value) >= 0, "No enough balance");
        wrapper.transfer(to, value);
    }

    public void transferOtherAsset(int assetChainId, int assetId, Address to, BigInteger value) {
        onlyOfficial();
        Token wrapper = new AssetWrapper(assetChainId, assetId);
        BigInteger balance = wrapper.balanceOf(Msg.address());
        require(balance.compareTo(value) >= 0, "No enough balance");
        wrapper.transfer(to, value);
    }

    public void transferProjectCandyAsset(Address to, BigInteger value) {
        onlyOfficial();
        Token wrapper;
        if (pi.candyAssetChainId + pi.candyAssetId == 0) {
            wrapper = new NRC20Wrapper(pi.candyToken);
        } else {
            wrapper = new AssetWrapper(pi.candyAssetChainId, pi.candyAssetId);
        }
        BigInteger balance = wrapper.balanceOf(Msg.address());
        require(balance.compareTo(value) >= 0, "No enough balance");
        wrapper.transfer(to, value);
    }

    protected void setPocmInfo(PocmInfo pi) {
        this.pi = pi;
    }

    /**
     * 转移owner
     */
    class OwnershipTransferredEvent implements Event {

        //先前拥有者
        private Address previousOwner;

        //新的拥有者
        private Address newOwner;

        public OwnershipTransferredEvent(Address previousOwner, Address newOwner) {
            this.previousOwner = previousOwner;
            this.newOwner = newOwner;
        }

        public Address getPreviousOwner() {
            return previousOwner;
        }

        public void setPreviousOwner(Address previousOwner) {
            this.previousOwner = previousOwner;
        }

        public Address getNewOwner() {
            return newOwner;
        }

        public void setNewOwner(Address newOwner) {
            this.newOwner = newOwner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o){
                return true;
            }
            if (o == null || getClass() != o.getClass()){
                return false;
            }

            OwnershipTransferredEvent that = (OwnershipTransferredEvent) o;

            if (previousOwner != null ? !previousOwner.equals(that.previousOwner) : that.previousOwner != null){
                return false;
            }

            return newOwner != null ? newOwner.equals(that.newOwner) : that.newOwner == null;
        }

        @Override
        public int hashCode() {
            int result = previousOwner != null ? previousOwner.hashCode() : 0;
            result = 31 * result + (newOwner != null ? newOwner.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "OwnershipTransferredEvent{" +
                    "previousOwner=" + previousOwner +
                    ", newOwner=" + newOwner +
                    '}';
        }

    }

}
