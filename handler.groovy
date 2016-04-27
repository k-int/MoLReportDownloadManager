@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.1'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.1'),
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

pullLatest(config)

cfg_file << toJson(config);

System.exit(0);

def pullLatest(config) {
  Properties props = new Properties()
  props.setProperty("mail.store.protocol", "imap")
  props.setProperty("mail.imap.host", host)
  props.setProperty("mail.imap.port", port)
  def session = Session.getDefaultInstance(props, null)
  def store = session.getStore("imap")
  def inbox
   
  try {
    store.connect(host, username, password)
    inbox = openFolder(store, "INBOX")
    def messages = inbox.search( new FlagTerm(new Flags(Flags.Flag.DELETED), false))
    messages.each { msg ->
      println("${msg.subject} ${msg.sender}")
      msg.setFlag(Flags.Flag.SEEN, true)
    }
  } finally {
    if(inbox) {
      inbox.close(true)
    }
    store.close()
  }
}
