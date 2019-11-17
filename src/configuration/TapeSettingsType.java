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
 * <p>Classe Java de TapeSettingsType complex type.
 * 
 * <p>O seguinte fragmento do esquema especifica o conteúdo esperado contido dentro desta classe.
 * 
 * <pre>
 * &lt;complexType name="TapeSettingsType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="enableLoadTraps" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="accelerateLoading" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="enableSaveTraps" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="highSamplingFreq" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="flashLoad" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="autoLoadTape" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="invertedEar" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TapeSettingsType", propOrder = {
    "enableLoadTraps",
    "accelerateLoading",
    "enableSaveTraps",
    "highSamplingFreq",
    "flashLoad",
    "autoLoadTape",
    "invertedEar"
})
public class TapeSettingsType {

    @XmlElement(defaultValue = "true")
    protected boolean enableLoadTraps;
    @XmlElement(defaultValue = "true")
    protected boolean accelerateLoading;
    @XmlElement(defaultValue = "true")
    protected boolean enableSaveTraps;
    @XmlElement(defaultValue = "false")
    protected boolean highSamplingFreq;
    @XmlElement(defaultValue = "false")
    protected boolean flashLoad;
    @XmlElement(defaultValue = "true")
    protected boolean autoLoadTape;
    @XmlElement(defaultValue = "false")
    protected boolean invertedEar;

    /**
     * Obtém o valor da propriedade enableLoadTraps.
     * 
     */
    public boolean isEnableLoadTraps() {
        return enableLoadTraps;
    }

    /**
     * Define o valor da propriedade enableLoadTraps.
     * 
     */
    public void setEnableLoadTraps(boolean value) {
        this.enableLoadTraps = value;
    }

    /**
     * Obtém o valor da propriedade accelerateLoading.
     * 
     */
    public boolean isAccelerateLoading() {
        return accelerateLoading;
    }

    /**
     * Define o valor da propriedade accelerateLoading.
     * 
     */
    public void setAccelerateLoading(boolean value) {
        this.accelerateLoading = value;
    }

    /**
     * Obtém o valor da propriedade enableSaveTraps.
     * 
     */
    public boolean isEnableSaveTraps() {
        return enableSaveTraps;
    }

    /**
     * Define o valor da propriedade enableSaveTraps.
     * 
     */
    public void setEnableSaveTraps(boolean value) {
        this.enableSaveTraps = value;
    }

    /**
     * Obtém o valor da propriedade highSamplingFreq.
     * 
     */
    public boolean isHighSamplingFreq() {
        return highSamplingFreq;
    }

    /**
     * Define o valor da propriedade highSamplingFreq.
     * 
     */
    public void setHighSamplingFreq(boolean value) {
        this.highSamplingFreq = value;
    }

    /**
     * Obtém o valor da propriedade flashLoad.
     * 
     */
    public boolean isFlashLoad() {
        return flashLoad;
    }

    /**
     * Define o valor da propriedade flashLoad.
     * 
     */
    public void setFlashLoad(boolean value) {
        this.flashLoad = value;
    }

    /**
     * Obtém o valor da propriedade autoLoadTape.
     * 
     */
    public boolean isAutoLoadTape() {
        return autoLoadTape;
    }

    /**
     * Define o valor da propriedade autoLoadTape.
     * 
     */
    public void setAutoLoadTape(boolean value) {
        this.autoLoadTape = value;
    }

    /**
     * Obtém o valor da propriedade invertedEar.
     * 
     */
    public boolean isInvertedEar() {
        return invertedEar;
    }

    /**
     * Define o valor da propriedade invertedEar.
     * 
     */
    public void setInvertedEar(boolean value) {
        this.invertedEar = value;
    }

}
