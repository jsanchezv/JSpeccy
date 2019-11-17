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
 * <p>Classe Java de Interface1Type complex type.
 * 
 * <p>O seguinte fragmento do esquema especifica o conteúdo esperado contido dentro desta classe.
 * 
 * <pre>
 * &lt;complexType name="Interface1Type"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="connectedIF1" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="microdriveUnits"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}byte"&gt;
 *               &lt;maxInclusive value="8"/&gt;
 *               &lt;minInclusive value="1"/&gt;
 *               &lt;totalDigits value="1"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="cartridgeSize"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}integer"&gt;
 *               &lt;minInclusive value="10"/&gt;
 *               &lt;maxInclusive value="254"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Interface1Type", propOrder = {
    "connectedIF1",
    "microdriveUnits",
    "cartridgeSize"
})
public class Interface1Type {

    @XmlElement(defaultValue = "false")
    protected boolean connectedIF1;
    @XmlElement(defaultValue = "8")
    protected byte microdriveUnits;
    @XmlElement(defaultValue = "180")
    protected int cartridgeSize;

    /**
     * Obtém o valor da propriedade connectedIF1.
     * 
     */
    public boolean isConnectedIF1() {
        return connectedIF1;
    }

    /**
     * Define o valor da propriedade connectedIF1.
     * 
     */
    public void setConnectedIF1(boolean value) {
        this.connectedIF1 = value;
    }

    /**
     * Obtém o valor da propriedade microdriveUnits.
     * 
     */
    public byte getMicrodriveUnits() {
        return microdriveUnits;
    }

    /**
     * Define o valor da propriedade microdriveUnits.
     * 
     */
    public void setMicrodriveUnits(byte value) {
        this.microdriveUnits = value;
    }

    /**
     * Obtém o valor da propriedade cartridgeSize.
     * 
     */
    public int getCartridgeSize() {
        return cartridgeSize;
    }

    /**
     * Define o valor da propriedade cartridgeSize.
     * 
     */
    public void setCartridgeSize(int value) {
        this.cartridgeSize = value;
    }

}
