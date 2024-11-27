# NULS AI Smart Contract Documentation

## Overview
The NULS AI smart contract system implements domain name registration, management, and staking functions on the NULS blockchain. It consists of three main contracts:

1. NulsDomain - Core AI identity management contract

2. NulsDomainNRC721 - NFT implementation of AI identity ownership

3. NulsDomainStaking - Staking and reward mechanism

##Core contract

### NulsDomain contract

####Key functions

##### AI identity management
- `mint(string domain)` - Register a new AI identity
- `mintWithTokenURI(string domain, string tokenURI)` - Register an AI identity with metadata
- `setTokenURI(BigInteger tokenId, string uri)` - Update AI identity metadata
- `setUserURI(string uri)` - Set user profile metadata

#####Domain name query
- `domain(BigInteger tokenId)` - Get AI identity by token ID
- `domainId(string domain)` - Get the token ID of the AI ​​identity
- `tokenURI(string domain)` - Get AI Identity Metadata URI
- `userAddress(string domain)` - Get the owner address AI Identity
- `userURI(Address user)` - Get the user profile metadata

##### AI Identity Pricing
- `getDefaultPrice()` - Get the default AI Identity price
- `getDomainPrice(int length)` - Get the price of a specific AI Identity length
- `getPriceByDomain(string domain)` - Get the price of a specific AI Identity

##### Staking and Rewards
- `activeAward(string domain)` - Activate AI Identity Staking Rewards
- `receiveAwards()` - Receive available rewards
- `pendingAward(Address user)` - View pending rewards
- `getUserRewardReceived(Address user)` - View total rewards received

### NulsDomainNRC721 Contract

#### Main Functions

##### Token Management
- `mint(Address to, BigInteger tokenId)` - mint new AI identity tokens
- `mintWithTokenURI(Address to, BigInteger tokenId, string tokenURI)` - mint with metadata
- `burn(Address Owner, BigInteger tokenId)` - destroy AI identity tokens
- `transferFrom(Address from, Address to, BigInteger tokenId)` - transfer AI identity ownership

##### Token query
- `ownerOf(BigInteger tokenId)` - get AI identity owner
- `balanceOf(Address Owner)` - get the number of AI identities owned
- `tokenURI(BigInteger tokenId)` - get AI identity metadata URI

### NulsDomainStaking contract

#### Key functions

##### Staking operations
- `depositForOwn()` - stake NULS tokens
- `withdraw(BigInteger amount)` - Withdraw staked tokens
- `quit()` - Completely exit staking
- `receiveAwards()` - Receive staking rewards

##### Staking query
- `pendingToken(Address User)` - View pending rewards
- `getDepositInfo(Address address)` - View staking details
- `totalDeposit()` - Get total staking amount
- `minimumStaking()` - Get minimum staking amount
- `lpSupply()` - Get total staking supply

## Events

### NulsDomain Events
- `DomainTransfer(Address from, Address to, string domain)`
- `UserActiveAward(string user, BigInteger userPay, string domain, bool newId)`
- `UserPendingAward(string user, BigInteger waiting)`

### NulsDomainStaking Events
- `DepositDetailInfoEvent(BigInteger value, long number, BigInteger amount)`
- `PocmWithdrawEvent(string user, string amount)`
- `CurrentMiningInfoEvent(List<CurrentMingInfo> info)`

## Example

### Register AI identity
```javascript
// Register a new domain name
contract.mint("mydomain.nuls");

// Register using metadata
contract.mintWithTokenURI("mydomain.nuls", "ipfs://metadata");
```

### Staking operation
```javascript
// Stake NULS
contract.depositForOwn({value: stakeAmount});

// Receive rewards
contract.receiveAwards();

// Withdraw stake
contract.withdraw(withdrawAmount);
```

### Query AI identity information
```javascript
// Get the owner of the AI ​​identity
const Owner = contract.userAddress("mydomain.nuls");

// Get the price of the AI ​​identity
const Price = contract.getPriceByDomain("mydomain.nuls");

// Check pending rewards
const Rewards = contract.pendingAward(userAddress);
```

## Security considerations

1. Reentrancy protection
- The contract implements reentrancy protection for key functions
- State changes occur before external calls

2. Access control
- Owner-only management operation functions
- Official-only protocol management functions
- Properly verify caller permissions

3. Input validation
- Domain name format and length check
- Amount verification for deposits/withdrawals
- Address verification for transfers

4. Economic security
- Minimum and maximum staking limits
- Locking staking period
- Treasury fee collection
- Consensus reward distribution control

## Error codes and messages

Common error messages:
- "AI identity already exists"
- "Insufficient payment"
- "Not the domain owner"
- "Amount is too small"
- "Stake is locked"
- "No rewards available"