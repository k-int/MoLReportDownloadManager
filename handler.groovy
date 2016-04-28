@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])



import javax.mail.*
import javax.mail.search.*
import java.util.Properties
import static groovy.json.JsonOutput.*
import groovy.json.JsonSlurper

import com.gargoylesoftware.htmlunit.*

println "Hello";

config = null;
cfg_file = new File('./handler-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
}

println("Using config ${config}");

println("Pulling latest messages");
pullLatest(config)

// println("Updating config");
// cfg_file << toJson(config);

System.exit(0);

def getReport(config, url) {
  def result = false;

  println("Get URL ${url}");
  client = new WebClient()
  client.getOptions().setThrowExceptionOnScriptError(false);
  client.getOptions().setJavaScriptEnabled(true);
  client.getOptions().setRedirectEnabled(true);
  client.getOptions().setCssEnabled(false);
  client.setAjaxController(new NicelyResynchronizingAjaxController());
  client.getCookieManager().setCookiesEnabled(true);
  client.waitForBackgroundJavaScript(4000);

  // Added as HtmlUnit had problems with the JavaScript
  // client.javaScriptEnabled = true
  html = client.getPage(url);
  // println html.anchors.collect{ it.hrefAttribute }.sort().unique().join('\n')

  println("Getting form");
  def form = html.getFormByName("form1");

  println("Getting login btn and user/pass fields");
  def login_button = html.getHtmlElementById('LoginControl_Login');
  def usernameField = form.getInputByName('LoginControl$UserName');
  def passwordField = form.getInputByName('LoginControl$Password');
  
  println("Set user/pass");
  usernameField.setValueAttribute(config.crm.user);
  passwordField.setValueAttribute(config.crm.pass);

  println("Click login btn");
  def dlfile = login_button.click();

  println(dlfile.getWebResponse().getContentAsStream().text);

  // println("Done :: ${dlfile.class.name} ${dlfile}");
  result
}

def pullLatest(config) {
  Properties props = new Properties()
  props.setProperty("mail.store.protocol", config.email.protocol)
  props.setProperty("mail.imap.host", config.email.host)
  props.setProperty("mail.imap.port", config.email.port)
  props.setProperty("mail.store.protocol", "imaps");
  // props.setProperty("mail.transport.protocol", config.email.transport)
  // props.setProperty("mail.smtp.auth", "true");
  props.setProperty("mail.smtp.starttls.enable", "true");
  // props.setProperty("mail.debug", "true");
  // props.setProperty("mail.smtp.debug", "true");
  props.setProperty("mail.imap.ssl.enable", "true");
  def session = Session.getDefaultInstance(props, null)
  def store = session.getStore("imaps")
  def folder
   
  try {
    store.connect(config.email.host, config.email.user, config.email.pass)
    folder = store.getFolder("inbox")

    if(!folder.isOpen())
      folder.open(Folder.READ_WRITE);

    // Find all messages not deleted and containing the subject line text report collection test
    def messages = folder.search( 
      new AndTerm(
        new SubjectTerm("report collection test"),
        new FlagTerm(new Flags(Flags.Flag.DELETED), false)));

    messages.each { msg ->
      // println("${msg.subject} ${msg.sender}")
      // println("${msg.inputStream.text}"); 
      if ( msg.content instanceof javax.mail.Multipart ) {
        // println("Message contains: ${msg.content.count} parts"); 
        // println("Content: ${msg.content}"); 
        // Get multipart 0 -- should be the text version
        def body_part_zero = msg.content.getBodyPart(0);
        // println("Part 0 : ${body_part_zero.inputStream.text}");
        def matcher = body_part_zero.inputStream.text =~ /<https:.*CollectReport\.aspx.*csv>/

        matcher.each { 
          // println "Message contains URL : ${it}"
          // Trim the < and > from the front and back of the string
          def url = it.substring(1,it.length()-1)

          // println("Url : \"${url}\"");
          if ( getReport(config, url) ) {
            println("GetReport completed successfully, call msg.setFlag(Flags.Flag.SEEN, true)");
          }
        }
      }
      else {
      }
      // msg.setFlag(Flags.Flag.SEEN, true)
    }
  } finally {
    if(folder) {
      folder.close(true)
    }
    store.close()
  }
}
