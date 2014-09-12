package nu.postnummeruppror.insamlingsappen.sources.valmyndigheten;

import net.sf.saxon.lib.NamespaceConstant;
import nu.postnummeruppror.insamlingsappen.client.CreateLocationSample;
import nu.postnummeruppror.insamlingsappen.client.SetAccount;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.xerces.parsers.DOMParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import se.kodapan.util.geography.gausskruger.Projection;
import se.kodapan.util.geography.gausskruger.Rt90_2p5_gon_v;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author kalle
 * @since 2014-09-12 15:02
 */
public class ImportRostningslokaler {

  public static void main(String[] args) throws Exception {

    SetAccount setAccount = new SetAccount();

    setAccount.setIdentity(UUID.randomUUID().toString());
    setAccount.setAcceptingCcZero(true);
    setAccount.setEmailAddress("valmyndigheten@postnummeruppror.nu");
    setAccount.setFirstName("Valmyndigheten");
    setAccount.setFirstName("Förtidsröstningslokaler 2014");
    setAccount.run();

    new ImportRostningslokaler().importXml(setAccount.getIdentity(), "http://www.val.se/val/val2014/rostmottagning/fortidsrostning/rostlokal.xml");
  }


  private static Logger log = LoggerFactory.getLogger(ImportRostningslokaler.class);


  public void importXml(String accountIdentity, String URL) throws Exception {

    Pattern streetNameAndHouseNumberPattern = Pattern.compile("^\\s*(.+)\\s+([0-9]+)(\\s*[A-Za-z])?\\s*$");

    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet get = new HttpGet(URL);
    CloseableHttpResponse response = httpclient.execute(get);

    ByteArrayOutputStream xmlBytes = new ByteArrayOutputStream(49152);
    IOUtils.copy(response.getEntity().getContent(), xmlBytes);

    response.getEntity().getContent().close();
    response.close();

    Projection projection = new Rt90_2p5_gon_v();

    DOMParser parser = new DOMParser();
    parser.parse(new InputSource(new ByteArrayInputStream(xmlBytes.toByteArray())));
    Document document = parser.getDocument();

    System.setProperty("javax.xml.xpath.XPathFactory:" + NamespaceConstant.OBJECT_MODEL_SAXON, net.sf.saxon.xpath.XPathFactoryImpl.class.getName());
    XPathFactory xPathfactory = XPathFactory.newInstance(NamespaceConstant.OBJECT_MODEL_SAXON);
    XPath xpath = xPathfactory.newXPath();

    XPathExpression länExpression = xpath.compile("//LÄN");
    XPathExpression kommunerExpression = xpath.compile("KOMMUN");
    XPathExpression röstningslokalExpression = xpath.compile("RÖSTNINGSLOKAL");

    NodeList länNodes = (NodeList) länExpression.evaluate(document, XPathConstants.NODESET);
    for (int länIndex = 0; länIndex < länNodes.getLength(); länIndex++) {
      Node länNode = länNodes.item(länIndex);

      NodeList kommunNodes = (NodeList) kommunerExpression.evaluate(länNode, XPathConstants.NODESET);
      for (int kommunIndex = 0; kommunIndex < kommunNodes.getLength(); kommunIndex++) {
        Node kommunNode = kommunNodes.item(kommunIndex);

        NodeList röstningslokalNodes = (NodeList) röstningslokalExpression.evaluate(kommunNode, XPathConstants.NODESET);
        for (int röstningslokalIndex = 0; röstningslokalIndex < röstningslokalNodes.getLength(); röstningslokalIndex++) {
          Node röstningslokalNode = röstningslokalNodes.item(röstningslokalIndex);

          CreateLocationSample createLocationSample = new CreateLocationSample();


          String gatuAdress = röstningslokalNode.getAttributes().getNamedItem("ADRESS2").getTextContent();
          if (!gatuAdress.isEmpty()) {

            Matcher matcher = streetNameAndHouseNumberPattern.matcher(gatuAdress);
            if (matcher.find()) {
              createLocationSample.setStreetName(matcher.group(1));
              createLocationSample.setHouseNumber(matcher.group(2));
              if (matcher.group(3) != null) {
                createLocationSample.setHouseName(matcher.group(3));
              }
            }

          }


          String postort = röstningslokalNode.getAttributes().getNamedItem("ORT").getTextContent().trim();
          if (!postort.isEmpty()) {
            createLocationSample.setPostalTown(postort);
          }

          if (!röstningslokalNode.getAttributes().getNamedItem("X").getTextContent().isEmpty()) {
            double x = Double.valueOf(röstningslokalNode.getAttributes().getNamedItem("X").getTextContent());
            double y = Double.valueOf(röstningslokalNode.getAttributes().getNamedItem("Y").getTextContent());

            projection.grid_to_geodetic(x, y);

            createLocationSample.setLatitude(projection.getLatitude());
            createLocationSample.setLongitude(projection.getLongitude());


            createLocationSample.run();

            System.currentTimeMillis();

          }



        }


      }
    }



  }


}
