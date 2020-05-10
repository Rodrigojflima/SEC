package org.announcementserver.ws.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.announcementserver.common.CryptoTools;
import org.announcementserver.common.Constants;
import org.announcementserver.ws.AnnouncementMessage;
import org.announcementserver.ws.AnnouncementServerPortType;
import org.announcementserver.ws.AnnouncementServerService;

import org.announcementserver.ws.RegisterRet;
import org.announcementserver.ws.ReadRet;
import org.announcementserver.ws.WriteRet;
import org.announcementserver.ws.WriteBackRet;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.announcementserver.ws.EmptyBoardFault_Exception;
import org.announcementserver.ws.InvalidNumberFault_Exception;
import org.announcementserver.ws.MessageSizeFault_Exception;
import org.announcementserver.ws.NumberPostsFault_Exception;
import org.announcementserver.ws.PostTypeFault_Exception;
import org.announcementserver.ws.ReferredAnnouncementFault_Exception;
import org.announcementserver.ws.ReferredUserFault_Exception;
import org.announcementserver.ws.UserNotRegisteredFault_Exception;

import javax.xml.ws.BindingProvider;
import static javax.xml.ws.BindingProvider.ENDPOINT_ADDRESS_PROPERTY;

public class FrontEnd {
    List<AnnouncementServerPortType> ports = null;
    List<String> wsUrls = null;
    AnnouncementServerPortType client = null;
    String username = null;
    Integer sn;
    String publicKey;
    List<String> response;
    Integer nServ;
    Integer f;
    Integer quorum;
    Integer wts = -1;
    Integer rid = -1;

    boolean verbose = false;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public FrontEnd(String host, String faults) throws AnnouncementServerClientException {
        wsUrls = new ArrayList<>();
        ports = new ArrayList<>();
        f = Integer.valueOf(faults);
        nServ = 3 * f + 1;
        quorum = (nServ + f) / 2;
        System.out.println(String.format("NServ: %d", nServ));

        for (Integer i = 1; i <= nServ; i++) {
            wsUrls.add(String.format(Constants.WS_NAME_FORMAT, host, Constants.PORT_START + i));
        }

        createStub();

        if (nServ == 1)
            client = ports.get(0);
    }

    public void init(String username) throws NoSuchAlgorithmException, UnrecoverableEntryException, KeyStoreException,
            CertificateException, IOException {
        this.username = username;
        this.publicKey = CryptoTools.publicKeyAsString(CryptoTools.getPublicKey(username));
    }

    public void checkInit() {
        if (this.username == null)
            throw new RuntimeException("Username not Initialized");
    }

    public synchronized String register() throws InvalidKeyException, CertificateException, KeyStoreException,
            NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
            UnrecoverableEntryException, IOException {

        checkInit();
        Client cli;
        List<RegisterRet> responses = new ArrayList<>(nServ);

        for (int i = 1; i <= nServ; i++) {
            cli = new Client(this, Operation.REGISTER, i);
            cli.regRets = responses;
            cli.start();
        }

        while (responses.size() <= quorum) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        RegisterRet response = responses.get(0); //FIXME vulnerable to byzantine servers

        sn = response.getSeqNumber();
        wts = response.getWts();
        rid = response.getRid();

        return "Register successfull! Welcome user!";
    }

    public synchronized String post(String message, List<String> announcementList)
            throws InvalidKeyException, CertificateException, KeyStoreException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, UnrecoverableEntryException,
            IOException, MessageSizeFault_Exception, PostTypeFault_Exception, ReferredAnnouncementFault_Exception,
            ReferredUserFault_Exception, UserNotRegisteredFault_Exception {

        checkInit();
        Client cli;

        wts++;
        List<WriteRet> ackList = new ArrayList<>(nServ);
        
        this.response = null;
        for (int i = 1; i <= nServ; i++) {
            cli = new Client(this, Operation.POST, i);
            cli.message = message;
            cli.references = announcementList;
            cli.seqNumber = sn;
            cli.wts = wts;
            cli.writeRets = ackList;
            cli.start();
        }
        
        while (ackList.size() <= quorum) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        sn++;

        return "Post was successfully posted to Personal Board!";
    }

    public synchronized String postGeneral(String message, List<String> announcementList)
            throws NoSuchAlgorithmException, UnrecoverableEntryException, KeyStoreException, CertificateException,
            IOException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException,
            MessageSizeFault_Exception, PostTypeFault_Exception, ReferredAnnouncementFault_Exception,
            ReferredUserFault_Exception, UserNotRegisteredFault_Exception {

        checkInit();
        Client cli;

        // READ PHASE: obtain highest wts

        List<ReadRet> readList = new ArrayList<>(nServ);

        rid++;

        for (int i = 1; i <= nServ; i++) {
            System.out.println(String.valueOf(i) + "th client");
            cli = new Client(this, Operation.READGENERAL, i);
            cli.number = 1;
            cli.seqNumber = sn;
            cli.rid = rid;
            cli.readRets = readList;
            cli.start();
        }

        while (readList.size() <= quorum) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Read Done");

        sn++;

        Integer nwts = highestWts(readList);
        System.out.println(String.format("Highest wts: %d", nwts));

        // WRITE PHASE: write the wts with highest wts + 1

        List<WriteRet> ackList = new ArrayList<>(nServ);
        
        response = null;
        for (int i = 1; i <= nServ; i++) {
            cli = new Client(this, Operation.POSTGENERAL, i);
            cli.message = message;
            cli.references = announcementList;
            cli.seqNumber = sn;
            cli.wts = nwts + 1;
            cli.writeRets = ackList;
            cli.start();
        }
        
        while (ackList.size() <= quorum) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        sn++;

        return "Post was successfully posted to General Board!";
    }

    public synchronized String read(String clientID, Integer number) throws NoSuchAlgorithmException, UnrecoverableEntryException,
            KeyStoreException, CertificateException, IOException, EmptyBoardFault_Exception,
            InvalidNumberFault_Exception, NumberPostsFault_Exception, ReferredUserFault_Exception, InvalidKeyException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {

        checkInit();
        Client cli;
        List<ReadRet> readList = new ArrayList<>(nServ);
        List<WriteBackRet> ackList = new ArrayList<>(nServ);
        rid++;
        
        this.response = null;
        for (int i = 1; i <= nServ; i++) {
            cli = new Client(this, Operation.READ, i);
            cli.seqNumber = sn;
            cli.number = number;
            cli.clientID = clientID;
            cli.rid = rid;
            cli.readRets = readList;
            cli.start();
        }
        
        while (readList.size() <= quorum) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        ReadRet ret = highestVal(readList);
        
        sn++;
        
        // Write Back Phase
        
        if(!ret.getAnnouncements().isEmpty()) { //not sure if that is enough (the intention is: ret has no posts, no need to do write back)
            for (int i = 1; i <= nServ; i++) {
            	cli = new Client(this, Operation.WRITEBACK, i);
            	cli.seqNumber = sn;
            	cli.writeBack = ret;
            	cli.writeBackRets = ackList;
            	cli.start();
            }
            
            while (ackList.size() <= quorum) {
            	try {
            		wait();
            	} catch (InterruptedException e) {
            		e.printStackTrace();
            	}
            }
            
            sn++;
        }
        
        return postsToString(ret);
    }

    public synchronized String readGeneral(Integer number) throws NoSuchAlgorithmException, UnrecoverableEntryException,
            KeyStoreException, CertificateException, IOException, EmptyBoardFault_Exception,
            InvalidNumberFault_Exception, NumberPostsFault_Exception, InvalidKeyException, NoSuchPaddingException,
            IllegalBlockSizeException, BadPaddingException {

        checkInit();
        Client cli;
        List<ReadRet> readList = new ArrayList<>(nServ);
        rid++;
        
        response = null;
        for (int i = 1; i <= nServ; i++) {
            cli = new Client(this, Operation.READGENERAL, i);
            cli.seqNumber = sn;
            cli.number = number;
            cli.rid = rid;
            cli.readRets = readList;
            cli.start();
        }
        
        while (readList.size() <= quorum) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        ReadRet ret = highestVal(readList);
        
        sn++;

        return postsToString(ret);
    }

    private void createStub() {
        AnnouncementServerPortType port;
        AnnouncementServerService service;
        for (String wsUrl: wsUrls) {
            if (verbose)
                System.out.println("Creating stub ...");
            service = new AnnouncementServerService();
            port = service.getAnnouncementServerPort();

            if (verbose)
                System.out.println("Setting endpoint address ...");
            BindingProvider bindingProvider = (BindingProvider) port;
            Map<String, Object> requestContext = bindingProvider.getRequestContext();
            requestContext.put(ENDPOINT_ADDRESS_PROPERTY, wsUrl);

            ports.add(port);
            if (verbose) {
                System.out.print("Added client for: ");
                System.out.println(wsUrl);    
            }
        }
    }

    // AUXILIARY FUNCTIONS

    /*
    * used for postGeneral decision on highest wts (which means, each ret only has one post)
    */
    private Integer highestWts(List<ReadRet> readList) {
        Integer res = 0;

        for (ReadRet ret: readList) {
            if (ret.getAnnouncements().isEmpty()) continue;
            System.out.println(postToString(ret.getAnnouncements().get(0)));
            if (ret.getAnnouncements().get(0).getWts() > res) 
                res = ret.getAnnouncements().get(0).getWts(); // only one post
        }

        return res;
    }

    private String postToString(AnnouncementMessage post) {
        return String.format("Author: %s, Id: %d\n\"%s\"\nReferences: %s\n",
            post.getWriter(), post.getWts(), post.getMessage(),
            post.getAnnouncementList().toString());
    }

    private ReadRet highestVal(List<ReadRet> readList) {
        Integer highTs = 0;
        String highWriter = null;
        ReadRet high = null;
        List<AnnouncementMessage> list;
        AnnouncementMessage temp;

        for (ReadRet ret: readList) {
            list = ret.getAnnouncements();
            if (list.isEmpty()) continue;
            System.out.println(postToString(ret.getAnnouncements().get(0)));
            temp = list.get(list.size() - 1); // most recent post

            // higher if ts is bigger or, if they're same, lowest client id (decided by Java default String comparison)
            if (temp.getWts() > highTs || (temp.getWts() == highTs && temp.getWriter().compareTo(highWriter) < 0)) {
                highTs = temp.getWts(); // only one post
                highWriter = temp.getWriter();
                high = ret;
            }
        }

        return high;
    }

    private String postsToString(ReadRet response) {
        String res = "";

        for (AnnouncementMessage post: response.getAnnouncements()) {
            res += postToString(post);
        }

        return res;
    }
}