package com.example.demo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Properties;
import java.util.Set;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Identities;
import org.hyperledger.fabric.gateway.Identity;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallets;
import org.hyperledger.fabric.gateway.X509Identity;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.security.CryptoSuiteFactory;
import org.hyperledger.fabric_ca.sdk.Attribute;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

	@GetMapping("/enrollAdmin")
	public String enrollAdmin() throws Exception {
		Properties props = new Properties();
		props.put("pemFile", "ca.org1.example.com-cert.pem");
		props.put("allowAllHostNames", "true");
		HFCAClient caClient = HFCAClient.createNewInstance("https://localhost:7054", props);
		CryptoSuite cryptoSuite = CryptoSuiteFactory.getDefault().getCryptoSuite();
		caClient.setCryptoSuite(cryptoSuite);

		// Create a wallet for managing identities
		Wallet wallet = Wallets.newFileSystemWallet(Paths.get("wallet"));

		// Check to see if we've already enrolled the admin user.
		if (wallet.get("admin") != null) {
			System.out.println("An identity for the admin user \"admin\" already exists in the wallet");
			return "An identity for the admin user \"admin\" already exists in the wallet";
		}

		// Enroll the admin user, and import the new identity into the wallet.
		final EnrollmentRequest enrollmentRequestTLS = new EnrollmentRequest();
		enrollmentRequestTLS.addHost("localhost");
		enrollmentRequestTLS.setProfile("tls");
		Enrollment enrollment = caClient.enroll("admin", "adminpw", enrollmentRequestTLS);
		Identity user = Identities.newX509Identity("Org1MSP", enrollment);
		wallet.put("admin", user);
		System.out.println("Successfully enrolled user \"admin\" and imported it into the wallet");
		return "Successfully enrolled user \"admin\" and imported it into the wallet";
	}

	@GetMapping("/registerUser")
	public String registerUser(@RequestParam String userName) throws Exception {
		Properties props = new Properties();
		props.put("pemFile", "ca.org1.example.com-cert.pem");
		props.put("allowAllHostNames", "true");
		HFCAClient caClient = HFCAClient.createNewInstance("https://localhost:7054", props);
		CryptoSuite cryptoSuite = CryptoSuiteFactory.getDefault().getCryptoSuite();
		caClient.setCryptoSuite(cryptoSuite);

		// Create a wallet for managing identities
		Wallet wallet = Wallets.newFileSystemWallet(Paths.get("wallet"));

		// Check to see if we've already enrolled the user.
		if (wallet.get(userName) != null) {
			System.out.println("An identity for the user "+userName+" already exists in the wallet");
			return "An identity for the user "+userName+" already exists in the wallet";
		}

		X509Identity adminIdentity = (X509Identity) wallet.get("admin");
		if (adminIdentity == null) {
			System.out.println("\"admin\" needs to be enrolled and added to the wallet first");
			return "\"admin\" needs to be enrolled and added to the wallet first";
		}
		User admin = new User() {

			
			
			@Override
			public String getName() {
				return "admin";
			}

			@Override
			public Set<String> getRoles() {
				return null;
			}

			@Override
			public String getAccount() {
				return null;
			}

			@Override
			public String getAffiliation() {
				return "org1.department1";
			}
			
			

			@Override
			public Enrollment getEnrollment() {
				return new Enrollment() {

					@Override
					public PrivateKey getKey() {
						return adminIdentity.getPrivateKey();
					}

					@Override
					public String getCert() {
						return Identities.toPemString(adminIdentity.getCertificate());
					}
				
				};
			}

			@Override
			public String getMspId() {
				return "Org1MSP";
			}

		};

		// Register the user, enroll the user, and import the new identity into the
		// wallet.
		RegistrationRequest registrationRequest = new RegistrationRequest(userName);
		registrationRequest.setAffiliation("org1.department1");
		registrationRequest.setEnrollmentID(userName);
		
		
		Attribute attr = new Attribute("role","user",true);
		registrationRequest.addAttribute(attr);
		
		
		String enrollmentSecret = caClient.register(registrationRequest, admin);
		Enrollment enrollment = caClient.enroll(userName, enrollmentSecret);
		Identity user = Identities.newX509Identity("Org1MSP", enrollment);
		wallet.put(userName, user);
		System.out.println("Successfully enrolled user "+userName+" and imported it into the wallet");
		return "Successfully enrolled user "+userName+" and imported it into the wallet";
	}

	@GetMapping("/initLedger")
	public String initLedger(@RequestParam String userName) throws Exception {
		Path walletPath = Paths.get("wallet");
		Wallet wallet = Wallets.newFileSystemWallet(walletPath);
		// load a CCP
		Path networkConfigPath = Paths.get("connection-org1.yaml");

		Gateway.Builder builder = Gateway.createBuilder();
		builder.identity(wallet, userName).networkConfig(networkConfigPath).discovery(true);

		// create a gateway connection
		try (Gateway gateway = builder.connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("productcc");

			byte[] result;

			result = contract.submitTransaction("InitLedger");
			System.out.println(new String(result));

			// contract.submitTransaction("createCar", "CAR10", "VW", "Polo", "Grey",
			// "Mary");

			// result = contract.evaluateTransaction("queryCar", "CAR10");
			// System.out.println(new String(result));

			// contract.submitTransaction("changeCarOwner", "CAR10", "Archie");

			// result = contract.evaluateTransaction("queryCar", "CAR10");
			// System.out.println(new String(result));
			return new String(result);

		}

	}

	@GetMapping("/getProducts")
	public String getAllProducts(@RequestParam String userName) throws Exception {
		Path walletPath = Paths.get("wallet");
		Wallet wallet = Wallets.newFileSystemWallet(walletPath);
		// load a CCP
		Path networkConfigPath = Paths.get("connection-org1.yaml");

		Gateway.Builder builder = Gateway.createBuilder();
		builder.identity(wallet, userName).networkConfig(networkConfigPath).discovery(true);

		// create a gateway connection
		try (Gateway gateway = builder.connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("productcc");

			byte[] result;

			result = contract.evaluateTransaction("GetAllProducts");
			System.out.println(new String(result));

			return new String(result);

		}

	}

	@PostMapping("/createProduct")
	public String createProduct(@RequestBody Product product,@RequestParam String userName) throws Exception{
		Path walletPath = Paths.get("wallet");
		Wallet wallet = Wallets.newFileSystemWallet(walletPath);
		// load a CCP
		Path networkConfigPath = Paths.get("connection-org1.yaml");

		Gateway.Builder builder = Gateway.createBuilder();
		builder.identity(wallet, userName).networkConfig(networkConfigPath).discovery(true);

		// create a gateway connection
		try (Gateway gateway = builder.connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("productcc");
			byte[] result;

			result = contract.submitTransaction("CreateProduct",product.getProductId(),product.getProductName(),String.valueOf(product.getProductPrice()),product.getProductOwner(),product.getProductDetails());
			return  new String(result);
		}
	}
	
	@GetMapping("/viewProduct")
	public String viewProduct(@RequestParam String productId,@RequestParam String userName) throws Exception{
		Path walletPath = Paths.get("wallet");
		Wallet wallet = Wallets.newFileSystemWallet(walletPath);
		// load a CCP
		Path networkConfigPath = Paths.get("connection-org1.yaml");

		Gateway.Builder builder = Gateway.createBuilder();
		builder.identity(wallet, userName).networkConfig(networkConfigPath).discovery(true);

		// create a gateway connection
		try (Gateway gateway = builder.connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("productcc");
			byte[] result;

			result = contract.evaluateTransaction("ReadProduct",productId);
			return  new String(result);
		}
	}
	
	@DeleteMapping("/deleteProduct")
	public String deleteProduct(@RequestParam String productId,@RequestParam String userName) throws Exception{
		Path walletPath = Paths.get("wallet");
		Wallet wallet = Wallets.newFileSystemWallet(walletPath);
		// load a CCP
		Path networkConfigPath = Paths.get("connection-org1.yaml");

		Gateway.Builder builder = Gateway.createBuilder();
		builder.identity(wallet, userName).networkConfig(networkConfigPath).discovery(true);

		// create a gateway connection
		try (Gateway gateway = builder.connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("productcc");
			byte[] result;
			
			result = contract.submitTransaction("DeleteProduct",productId);
			
			return  new String(result);
		}
	}
	
	@DeleteMapping("/deleteProducts")
	public String deleteProducts(@RequestParam String userName) throws Exception{
		Path walletPath = Paths.get("wallet");
		Wallet wallet = Wallets.newFileSystemWallet(walletPath);
		// load a CCP
		Path networkConfigPath = Paths.get("connection-org1.yaml");

		Gateway.Builder builder = Gateway.createBuilder();
		builder.identity(wallet, userName).networkConfig(networkConfigPath).discovery(true);

		// create a gateway connection
		try (Gateway gateway = builder.connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("productcc");
			byte[] result;

			result = contract.submitTransaction("DeleteAllProducts");
			return  new String(result);
		}
	}
	
	
}
