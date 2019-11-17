//
// Este arquivo foi gerado pela Arquitetura JavaTM para Implementação de Referência (JAXB) de Bind XML, v2.3.0 
// Consulte <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Todas as modificações neste arquivo serão perdidas após a recompilação do esquema de origem. 
// Gerado em: 2019.11.17 às 01:44:49 PM BRT 
//


package configuration;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java de KeyboardJoystickType complex type.
 * 
 * <p>O seguinte fragmento do esquema especifica o conteúdo esperado contido dentro desta classe.
 * 
 * <pre>
 * &lt;complexType name="KeyboardJoystickType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="JoystickModel"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}integer"&gt;
 *               &lt;maxInclusive value="5"/&gt;
 *               &lt;minInclusive value="0"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="mapPCKeys" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="Issue2" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "KeyboardJoystickType", propOrder = {
    "joystickModel",
    "mapPCKeys",
    "issue2"
})
public class KeyboardJoystickType {

    @XmlElement(name = "JoystickModel", defaultValue = "0")
    protected int joystickModel;
    @XmlElement(defaultValue = "false")
    protected boolean mapPCKeys;
    @XmlElement(name = "Issue2", defaultValue = "false")
    protected boolean issue2;

    /**
     * Obtém o valor da propriedade joystickModel.
     * 
     */
    public int getJoystickModel() {
        return joystickModel;
    }

    /**
     * Define o valor da propriedade joystickModel.
     * 
     */
    public void setJoystickModel(int value) {
        this.joystickModel = value;
    }

    /**
     * Obtém o valor da propriedade mapPCKeys.
     * 
     */
    public boolean isMapPCKeys() {
        return mapPCKeys;
    }

    /**
     * Define o valor da propriedade mapPCKeys.
     * 
     */
    public void setMapPCKeys(boolean value) {
        this.mapPCKeys = value;
    }

    /**
     * Obtém o valor da propriedade issue2.
     * 
     */
    public boolean isIssue2() {
        return issue2;
    }

    /**
     * Define o valor da propriedade issue2.
     * 
     */
    public void setIssue2(boolean value) {
        this.issue2 = value;
    }

}
