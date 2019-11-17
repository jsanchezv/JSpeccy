//
// Este arquivo foi gerado pela Arquitetura JavaTM para Implementação de Referência (JAXB) de Bind XML, v2.3.0 
// Consulte <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Todas as modificações neste arquivo serão perdidas após a recompilação do esquema de origem. 
// Gerado em: 2019.11.17 às 01:44:49 PM BRT 
//


package configuration;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java de RecentFilesType complex type.
 * 
 * <p>O seguinte fragmento do esquema especifica o conteúdo esperado contido dentro desta classe.
 * 
 * <pre>
 * &lt;complexType name="RecentFilesType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="recentFile" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="5" minOccurs="0"/&gt;
 *         &lt;element name="lastSnapshotDir" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="lastTapeDir" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "RecentFilesType", propOrder = {
    "recentFile",
    "lastSnapshotDir",
    "lastTapeDir"
})
public class RecentFilesType {

    protected List<String> recentFile;
    @XmlElement(required = true)
    protected String lastSnapshotDir;
    @XmlElement(required = true)
    protected String lastTapeDir;

    /**
     * Gets the value of the recentFile property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the recentFile property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRecentFile().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getRecentFile() {
        if (recentFile == null) {
            recentFile = new ArrayList<String>();
        }
        return this.recentFile;
    }

    /**
     * Obtém o valor da propriedade lastSnapshotDir.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLastSnapshotDir() {
        return lastSnapshotDir;
    }

    /**
     * Define o valor da propriedade lastSnapshotDir.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLastSnapshotDir(String value) {
        this.lastSnapshotDir = value;
    }

    /**
     * Obtém o valor da propriedade lastTapeDir.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLastTapeDir() {
        return lastTapeDir;
    }

    /**
     * Define o valor da propriedade lastTapeDir.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLastTapeDir(String value) {
        this.lastTapeDir = value;
    }

}
