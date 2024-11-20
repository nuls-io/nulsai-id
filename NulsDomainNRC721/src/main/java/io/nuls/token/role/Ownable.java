package io.nuls.token.role;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Event;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.contract.sdk.annotation.View;
import io.nuls.contract.sdk.token.NRC20Wrapper;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static io.nuls.contract.sdk.Utils.emit;
import static io.nuls.contract.sdk.Utils.require;

public class Ownable {

    /**
     * 合约创建者
     */
    protected Address contractCreator;

    protected Address owner;
    protected Map<Address, Boolean> minters = new HashMap<Address, Boolean>();

    public Ownable() {
        this.owner = Msg.sender();
        this.contractCreator = this.owner;
        minters.put(owner, true);
    }

    @View
    public Address viewOwner() {
        return owner;
    }

    @View
    public String viewContractCreator() {
        return this.contractCreator != null ? this.contractCreator.toString() : "";
    }

    protected void onlyOwner() {
        require(Msg.sender().equals(owner), "Only the owner of the contract can execute it.");
    }

    protected void onlyMinter() {
        require(isMinter(Msg.sender()), "MinterRole: caller does not have the Minter role");
    }

    @View
    public boolean isMinter(Address address) {
        return minters.containsKey(address);
    }

    public void addMinter(Address address) {
        onlyMinter();
        minters.put(address, true);
    }

    public void renounceMinter() {
        minters.remove(Msg.sender());
    }

    /**
     * 转让合约所有权
     *
     * @param newOwner
     */
    public void transferOwnership(Address newOwner) {
        onlyOwner();
        require(newOwner != null, "Empty new owner");
        emit(new OwnershipTransferredEvent(owner, newOwner));
        owner = newOwner;
    }

    public void transferOtherNRC20(@Required Address nrc20, @Required Address to, @Required BigInteger value) {
        onlyOwner();
        require(!Msg.address().equals(nrc20), "Do nothing by yourself");
        require(nrc20.isContract(), "[" + nrc20.toString() + "] is not a contract address");
        NRC20Wrapper wrapper = new NRC20Wrapper(nrc20);
        BigInteger balance = wrapper.balanceOf(Msg.address());
        require(balance.compareTo(value) >= 0, "No enough balance");
        wrapper.transfer(to, value);
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
