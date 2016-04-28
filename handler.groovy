@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.1'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.1'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])



import javax.mail.*
import javax.mail.search.*
import java.util.Properties
import groovy.json.JsonOutput.*
import groovy.json.JsonSlurper
 
println "Hello";

config = null;
cfg_file = new File('./handler-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
}

println("Using config ${config}");

println("Pulling latest messages");
pullLatest(config)

println("Updating config");

cfg_file << toJson(config);

System.exit(0);

def pullLatest(config) {
  Properties props = new Properties()
  props.setProperty("mail.store.protocol", config.email.protocol)
  props.setProperty("mail.imap.host", config.email.host)
  props.setProperty("mail.imap.port", config.email.port)
  props.setProperty("mail.store.protocol", "imaps");
  // props.setProperty("mail.transport.protocol", config.email.transport)
  // props.setProperty("mail.smtp.auth", "true");
  props.setProperty("mail.smtp.starttls.enable", "true");
  props.setProperty("mail.debug", "true");
  props.setProperty("mail.smtp.debug", "true");
  props.setProperty("mail.imap.ssl.enable", "true");
  def session = Session.getDefaultInstance(props, null)
  def store = session.getStore("imaps")
  def folder
   
  try {
    store.connect(config.email.host, config.email.user, config.email.pass)
    folder = store.getFolder("inbox")

    if(!folder.isOpen())
      folder.open(Folder.READ_WRITE);

    Message[] messages = folder.getMessages();
    System.out.println("No of Messages : " + folder.getMessageCount());
    System.out.println("No of Unread Messages : " + folder.getUnreadMessageCount());
    System.out.println(messages.length);
    for (int i=0; i < messages.length;i++) {
      System.out.println("*****************************************************************************");
      System.out.println("MESSAGE " + (i + 1) + ":");
      Message msg =  messages[i];
      //System.out.println(msg.getMessageNumber());
      //Object String;
      //System.out.println(folder.getUID(msg)

      subject = msg.getSubject();

      System.out.println("Subject: " + subject);
      System.out.println("From: " + msg.getFrom()[0]);
      System.out.println("To: "+msg.getAllRecipients()[0]);
      System.out.println("Date: "+msg.getReceivedDate());
      System.out.println("Size: "+msg.getSize());
      System.out.println(msg.getFlags());
      System.out.println("Body: \n"+ msg.getContent());
      System.out.println(msg.getContentType());
    }

    // def messages = inbox.search( new FlagTerm(new Flags(Flags.Flag.DELETED), false))
    // messages.each { msg ->
    //   println("${msg.subject} ${msg.sender}")
      // msg.setFlag(Flags.Flag.SEEN, true)
    // }
  } finally {
    if(folder) {
      folder.close(true)
    }
    store.close()
  }
}
