package org.announcementserver.ws.cli;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.xml.ws.WebServiceException;

import org.announcementserver.common.Constants;
import org.announcementserver.common.CryptoTools;
import org.announcementserver.ws.AnnouncementServerPortType;
import org.announcementserver.ws.EmptyBoardFault_Exception;
import org.announcementserver.ws.InvalidNumberFault_Exception;
import org.announcementserver.ws.MessageSizeFault_Exception;
import org.announcementserver.ws.NumberPostsFault_Exception;
import org.announcementserver.ws.PostTypeFault_Exception;
import org.announcementserver.ws.ReferredAnnouncementFault_Exception;
import org.announcementserver.ws.ReferredUserFault_Exception;
import org.announcementserver.ws.UserNotRegisteredFault_Exception;

enum Operation {
    REGISTER, POST, POSTGENERAL, READ, READGENERAL
};

public class Client extends Thread {
    private FrontEnd parent;
    private Integer servId;
    private Operation op;

    public String message;
    public List<String> references;
    public String boardKey;
    public Long number;

    public Client(FrontEnd parent, Operation op, Integer id, String message, List<String> references, Long number) {
        this.parent = parent;
        this.op = op;
        this.servId = id;
        if (message != null) this.message = message;
        if (references != null) this.references = references;
        if (number != null) this.number = number;
    }

    @Override
    public void run() {
        AnnouncementServerPortType port = parent.ports.get(servId - 1);
        String servName = Constants.SERVER_NAME + servId.toString();
        String username = parent.username;
        String publicKey = parent.publicKey;
        String signature = "";
        String hash = null;
        List<String> ret = null;

        switch (this.op) {
            case REGISTER:
                List<String> toHash = new ArrayList<>();
                toHash.add(username);
                toHash.add(servName);
                toHash.add(publicKey);

                try {
                    signature = CryptoTools.makeSignature(toHash.toArray(new String[0]));
                } catch (InvalidKeyException | CertificateException | KeyStoreException | NoSuchAlgorithmException
                        | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException
                        | UnrecoverableEntryException | IOException e) {
                    e.printStackTrace();
                    return;
                }

                try {
                    ret = port.register(publicKey, signature);
                } catch (WebServiceException e) {
                    System.out.println("Hey, dead");
                    return;
                }

                toHash = new ArrayList<>();
                toHash.add(servName);
                toHash.add(username);
                toHash.add(ret.get(0));

                if (ret.size() == 3) { // Not a new user
                    toHash.add(ret.get(1));
                }

                hash = null;
                try {
                    hash = CryptoTools.decryptSignature(servName, ret.get(ret.size() - 1));
                } catch (InvalidKeyException | CertificateException | KeyStoreException | NoSuchAlgorithmException
                        | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException
                        | UnrecoverableEntryException | IOException e) {
                    e.printStackTrace();
                    return;
                }

                toHash.add(hash);

                try {
                    if (!CryptoTools.checkHash(toHash.toArray(new String[0]))) {
                        throw new RuntimeException("Hashes are not equal");
                    }                
                } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException
                        | CertificateException | IOException e) {
                    e.printStackTrace();
                    return;
                }
                
                break;
                
            case POST:
                toHash = new ArrayList<>();
                toHash.add(username);
                toHash.add(servName);
                toHash.add(parent.sn.toString());
                toHash.add(publicKey);
                toHash.add(message);
                toHash.addAll(references);

                try {
                	signature = CryptoTools.makeSignature(toHash.toArray(new String[0]));
                } catch (InvalidKeyException | CertificateException | KeyStoreException | NoSuchAlgorithmException
                		| NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException
                		| UnrecoverableEntryException | IOException e2) {
                	// TODO Auto-generated catch block
                	e2.printStackTrace();
                	return;
                }

                try {
                	ret = port.post(publicKey, message, references, signature);
                } catch (MessageSizeFault_Exception | PostTypeFault_Exception | ReferredAnnouncementFault_Exception
                		| ReferredUserFault_Exception | UserNotRegisteredFault_Exception e2) {
                	// TODO Auto-generated catch block
                	e2.printStackTrace();
                	return;
                }

                try {
                	hash = CryptoTools.decryptSignature(servName, ret.get(1));
                } catch (InvalidKeyException | CertificateException | KeyStoreException | NoSuchAlgorithmException
                		| NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException
                		| UnrecoverableEntryException | IOException e2) {
                	// TODO Auto-generated catch block
                	e2.printStackTrace();
                	return;
                }

                //String response;
                
                try {
                	if (CryptoTools.checkHash(servName, username, String.valueOf(parent.sn), ret.get(0), hash)) {
                		//response = ret.get(0);
                	} else {
                		throw new RuntimeException("Hashes are not equal");
                	}
                } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException | CertificateException
                		| IOException e2) {
                	// TODO Auto-generated catch block
                	e2.printStackTrace();
                	return;
                }
                
                break;
                
            case POSTGENERAL:
            	toHash = new ArrayList<>();
                toHash.add(username);
                toHash.add(servName);
                toHash.add(parent.sn.toString());
                toHash.add(publicKey);
                toHash.add(message);
                toHash.addAll(references);

                try {
                	signature = CryptoTools.makeSignature(toHash.toArray(new String[0]));
                } catch (InvalidKeyException | CertificateException | KeyStoreException | NoSuchAlgorithmException
                		| NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException
                		| UnrecoverableEntryException | IOException e1) {
                	e1.printStackTrace();
                	return;
                }
                
                try {
                	ret = port.postGeneral(publicKey, message, references, signature);
                } catch (WebServiceException | MessageSizeFault_Exception | PostTypeFault_Exception | ReferredAnnouncementFault_Exception | ReferredUserFault_Exception | UserNotRegisteredFault_Exception e1) {
                	System.out.println("Hey, dead");
                	e1.printStackTrace();
                	return;
                }

                try {
                	hash = CryptoTools.decryptSignature(servName, ret.get(1));
                } catch (InvalidKeyException | CertificateException | KeyStoreException | NoSuchAlgorithmException
                		| NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException
                		| UnrecoverableEntryException | IOException e1) {
                	e1.printStackTrace();
                	return;
                }
                
                //String response;

                try {
                	if (CryptoTools.checkHash(servName, username, String.valueOf(parent.sn), ret.get(0), hash)) {
                		//response = ret.get(0);
                	} else {
                		throw new RuntimeException("Hashes are not equal");
                	}
                } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException | CertificateException
                		| IOException e1) {
                	// TODO Auto-generated catch block
                	e1.printStackTrace();
                	return;
                }
            	
                break;
                
            case READ:
                try {
                    port.read(publicKey, boardKey, number, signature);
                } catch (EmptyBoardFault_Exception | InvalidNumberFault_Exception | NumberPostsFault_Exception
                        | ReferredUserFault_Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
				break;
            case READGENERAL:
                try {
                    port.readGeneral(publicKey, number, signature);
                } catch (EmptyBoardFault_Exception | InvalidNumberFault_Exception | NumberPostsFault_Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
				break;
        }
        
        synchronized(parent) {
            if (parent.response == null) parent.response = ret;
            //else System.out.println("\n");
            parent.notify();
        }
    }
}