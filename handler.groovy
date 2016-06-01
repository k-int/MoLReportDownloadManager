@Grapes([
        @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
        @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
        @Grab(group='javax.mail', module='mail', version='1.4.7'),
        @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
        @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.2'),
        @GrabExclude('org.codehaus.groovy:groovy-all')
])



import javax.mail.*
import javax.mail.search.*
import java.text.SimpleDateFormat
import groovy.json.JsonSlurper

import com.gargoylesoftware.htmlunit.*


config = null;
cfg_file = new File('./handler-cfg.json')
if ( cfg_file.exists() ) {
  config = new JsonSlurper().parseText(cfg_file.text);
  println "Config Loaded"
}
else
  println "cannot read config"

pullLatest(config, null)

// println("Updating config");
// cfg_file << toJson(config);

System.exit(0);

def getReport(config, url, client) {

  def result = false;
    client = new WebClient();
    client.getOptions().setTimeout(12500000)
    client.getOptions().setMaxInMemory(1250000000)
    client.getOptions().setThrowExceptionOnScriptError(false);
    client.getOptions().setJavaScriptEnabled(true);
    client.getOptions().setRedirectEnabled(true);
    client.getOptions().setCssEnabled(false);
    client.setAjaxController(new NicelyResynchronizingAjaxController());
    client.getCookieManager().setCookiesEnabled(true);
    client.waitForBackgroundJavaScript(12500000);

  // Added as HtmlUnit had problems with the JavaScript

  html = client.getPage(url);
//    println html.anchors.collect{ it.hrefAttribute }.sort().unique().join('\n')

  def form = html.getFormByName("form1");

  def login_button = html.getHtmlElementById('LoginControl_Login');
  def usernameField = form.getInputByName('LoginControl$UserName');
  def passwordField = form.getInputByName('LoginControl$Password');

  usernameField.setValueAttribute(config.crm.user);
  passwordField.setValueAttribute(config.crm.pass);

      def dlfile = login_button.click();

  def s = dlfile.getWebResponse().getContentAsStream().text
    if(s) {
        result = true
        int count = 0;
        def str = url.split("ClientFileName=")[1]
        String dates = config.properties.add_dates_to_filenames
		 def sdf1 = new SimpleDateFormat("dd_MM_yyyy HH_mm")
        if(dates.equalsIgnoreCase("true")){
			def str_arr = str.split("[.]")
            str = str_arr[0]+sdf1.format(new Date())+"."+str_arr[1]
			}
        def file = new File(str)
        String over = config.properties.overwrite_files
        String outputPath = config.properties.output_path
        if(outputPath.contains('''$date''')) {
            def date = new Date()
            def sdf = new SimpleDateFormat("dd-MM-yyyy")
            outputPath = outputPath.replace('''$date''', sdf.format(date))
        }
        if (System.properties['os.name'].toLowerCase().contains('windows')) {
            if(!outputPath.endsWith("\\"))
                outputPath = outputPath + "\\"
        } else {
            if(!outputPath.endsWith("/"))
                outputPath = outputPath + "/"
        }
        new File(outputPath).mkdirs()

        if(over.equalsIgnoreCase("false"))
            while(file.exists()){
                def str_arr = str.split("[.]")
                file = new File(outputPath+str_arr[0]+"("+ count++ +")."+str_arr[1])
            }
        else
            file = new File(outputPath+str)
        file.createNewFile()
        file << s
    }


//   println("Done :: ${dlfile.class.name} ${dlfile}");
  result
}

def pullLatest(config, client) {
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
  String subject = config.properties.email_subjectline


    try {
    store.connect(config.email.host, config.email.user, config.email.pass)
    folder = store.getFolder("inbox")

    if(!folder.isOpen())
      folder.open(Folder.READ_WRITE);

    // Find all messages not deleted and containing the subject line text report collection test
    def messages = folder.search(
            new AndTerm(
                    new SubjectTerm(subject),
                    new FlagTerm(new Flags(Flags.Flag.DELETED), false)));

      boolean first =  true
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
          if ( getReport(config, url, client) ) {
            println("GetReport completed successfully");
            String delete = config.properties.delete_read_emails
            if(delete.equalsIgnoreCase("true"))
                msg.setFlag(Flags.Flag.DELETED,true)
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
