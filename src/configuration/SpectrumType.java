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
 * <p>Classe Java de SpectrumType complex type.
 * 
 * <p>O seguinte fragmento do esquema especifica o conteúdo esperado contido dentro desta classe.
 * 
 * <pre>
 * &lt;complexType name="SpectrumType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="AYEnabled48k" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="mutedSound" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="loadingNoise" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="ULAplus" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="defaultModel"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}integer"&gt;
 *               &lt;maxInclusive value="5"/&gt;
 *               &lt;minInclusive value="0"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="framesInt"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}integer"&gt;
 *               &lt;maxInclusive value="10"/&gt;
 *               &lt;minInclusive value="2"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="zoomed" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="zoom"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}int"&gt;
 *               &lt;maxInclusive value="4"/&gt;
 *               &lt;minInclusive value="2"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="multifaceEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="mf128on48K" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="hifiSound" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="hibernateMode" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="lecEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="emulate128kBug" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="zoomMethod"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}integer"&gt;
 *               &lt;totalDigits value="1"/&gt;
 *               &lt;fractionDigits value="0"/&gt;
 *               &lt;minInclusive value="0"/&gt;
 *               &lt;maxInclusive value="2"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="filterMethod"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}integer"&gt;
 *               &lt;totalDigits value="1"/&gt;
 *               &lt;fractionDigits value="0"/&gt;
 *               &lt;maxInclusive value="2"/&gt;
 *               &lt;minInclusive value="0"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="scanLines" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="borderSize"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}integer"&gt;
 *               &lt;totalDigits value="1"/&gt;
 *               &lt;fractionDigits value="0"/&gt;
 *               &lt;minInclusive value="0"/&gt;
 *               &lt;maxInclusive value="3"/&gt;
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
@XmlType(name = "SpectrumType", propOrder = {
    "ayEnabled48K",
    "mutedSound",
    "loadingNoise",
    "ulAplus",
    "defaultModel",
    "framesInt",
    "zoomed",
    "zoom",
    "multifaceEnabled",
    "mf128On48K",
    "hifiSound",
    "hibernateMode",
    "lecEnabled",
    "emulate128KBug",
    "zoomMethod",
    "filterMethod",
    "scanLines",
    "borderSize"
})
public class SpectrumType {

    @XmlElement(name = "AYEnabled48k", defaultValue = "false")
    protected boolean ayEnabled48K;
    @XmlElement(defaultValue = "false")
    protected boolean mutedSound;
    @XmlElement(defaultValue = "true")
    protected boolean loadingNoise;
    @XmlElement(name = "ULAplus", defaultValue = "false")
    protected boolean ulAplus;
    @XmlElement(defaultValue = "1")
    protected int defaultModel;
    @XmlElement(defaultValue = "2")
    protected int framesInt;
    @XmlElement(defaultValue = "false")
    protected boolean zoomed;
    @XmlElement(defaultValue = "2")
    protected int zoom;
    @XmlElement(defaultValue = "false")
    protected boolean multifaceEnabled;
    @XmlElement(name = "mf128on48K", defaultValue = "false")
    protected boolean mf128On48K;
    @XmlElement(defaultValue = "false")
    protected boolean hifiSound;
    @XmlElement(defaultValue = "false")
    protected boolean hibernateMode;
    @XmlElement(defaultValue = "false")
    protected boolean lecEnabled;
    @XmlElement(name = "emulate128kBug", defaultValue = "false")
    protected boolean emulate128KBug;
    @XmlElement(defaultValue = "0")
    protected int zoomMethod;
    @XmlElement(defaultValue = "0")
    protected int filterMethod;
    @XmlElement(defaultValue = "false")
    protected boolean scanLines;
    @XmlElement(defaultValue = "1")
    protected int borderSize;

    /**
     * Obtém o valor da propriedade ayEnabled48K.
     * 
     */
    public boolean isAYEnabled48K() {
        return ayEnabled48K;
    }

    /**
     * Define o valor da propriedade ayEnabled48K.
     * 
     */
    public void setAYEnabled48K(boolean value) {
        this.ayEnabled48K = value;
    }

    /**
     * Obtém o valor da propriedade mutedSound.
     * 
     */
    public boolean isMutedSound() {
        return mutedSound;
    }

    /**
     * Define o valor da propriedade mutedSound.
     * 
     */
    public void setMutedSound(boolean value) {
        this.mutedSound = value;
    }

    /**
     * Obtém o valor da propriedade loadingNoise.
     * 
     */
    public boolean isLoadingNoise() {
        return loadingNoise;
    }

    /**
     * Define o valor da propriedade loadingNoise.
     * 
     */
    public void setLoadingNoise(boolean value) {
        this.loadingNoise = value;
    }

    /**
     * Obtém o valor da propriedade ulAplus.
     * 
     */
    public boolean isULAplus() {
        return ulAplus;
    }

    /**
     * Define o valor da propriedade ulAplus.
     * 
     */
    public void setULAplus(boolean value) {
        this.ulAplus = value;
    }

    /**
     * Obtém o valor da propriedade defaultModel.
     * 
     */
    public int getDefaultModel() {
        return defaultModel;
    }

    /**
     * Define o valor da propriedade defaultModel.
     * 
     */
    public void setDefaultModel(int value) {
        this.defaultModel = value;
    }

    /**
     * Obtém o valor da propriedade framesInt.
     * 
     */
    public int getFramesInt() {
        return framesInt;
    }

    /**
     * Define o valor da propriedade framesInt.
     * 
     */
    public void setFramesInt(int value) {
        this.framesInt = value;
    }

    /**
     * Obtém o valor da propriedade zoomed.
     * 
     */
    public boolean isZoomed() {
        return zoomed;
    }

    /**
     * Define o valor da propriedade zoomed.
     * 
     */
    public void setZoomed(boolean value) {
        this.zoomed = value;
    }

    /**
     * Obtém o valor da propriedade zoom.
     * 
     */
    public int getZoom() {
        return zoom;
    }

    /**
     * Define o valor da propriedade zoom.
     * 
     */
    public void setZoom(int value) {
        this.zoom = value;
    }

    /**
     * Obtém o valor da propriedade multifaceEnabled.
     * 
     */
    public boolean isMultifaceEnabled() {
        return multifaceEnabled;
    }

    /**
     * Define o valor da propriedade multifaceEnabled.
     * 
     */
    public void setMultifaceEnabled(boolean value) {
        this.multifaceEnabled = value;
    }

    /**
     * Obtém o valor da propriedade mf128On48K.
     * 
     */
    public boolean isMf128On48K() {
        return mf128On48K;
    }

    /**
     * Define o valor da propriedade mf128On48K.
     * 
     */
    public void setMf128On48K(boolean value) {
        this.mf128On48K = value;
    }

    /**
     * Obtém o valor da propriedade hifiSound.
     * 
     */
    public boolean isHifiSound() {
        return hifiSound;
    }

    /**
     * Define o valor da propriedade hifiSound.
     * 
     */
    public void setHifiSound(boolean value) {
        this.hifiSound = value;
    }

    /**
     * Obtém o valor da propriedade hibernateMode.
     * 
     */
    public boolean isHibernateMode() {
        return hibernateMode;
    }

    /**
     * Define o valor da propriedade hibernateMode.
     * 
     */
    public void setHibernateMode(boolean value) {
        this.hibernateMode = value;
    }

    /**
     * Obtém o valor da propriedade lecEnabled.
     * 
     */
    public boolean isLecEnabled() {
        return lecEnabled;
    }

    /**
     * Define o valor da propriedade lecEnabled.
     * 
     */
    public void setLecEnabled(boolean value) {
        this.lecEnabled = value;
    }

    /**
     * Obtém o valor da propriedade emulate128KBug.
     * 
     */
    public boolean isEmulate128KBug() {
        return emulate128KBug;
    }

    /**
     * Define o valor da propriedade emulate128KBug.
     * 
     */
    public void setEmulate128KBug(boolean value) {
        this.emulate128KBug = value;
    }

    /**
     * Obtém o valor da propriedade zoomMethod.
     * 
     */
    public int getZoomMethod() {
        return zoomMethod;
    }

    /**
     * Define o valor da propriedade zoomMethod.
     * 
     */
    public void setZoomMethod(int value) {
        this.zoomMethod = value;
    }

    /**
     * Obtém o valor da propriedade filterMethod.
     * 
     */
    public int getFilterMethod() {
        return filterMethod;
    }

    /**
     * Define o valor da propriedade filterMethod.
     * 
     */
    public void setFilterMethod(int value) {
        this.filterMethod = value;
    }

    /**
     * Obtém o valor da propriedade scanLines.
     * 
     */
    public boolean isScanLines() {
        return scanLines;
    }

    /**
     * Define o valor da propriedade scanLines.
     * 
     */
    public void setScanLines(boolean value) {
        this.scanLines = value;
    }

    /**
     * Obtém o valor da propriedade borderSize.
     * 
     */
    public int getBorderSize() {
        return borderSize;
    }

    /**
     * Define o valor da propriedade borderSize.
     * 
     */
    public void setBorderSize(int value) {
        this.borderSize = value;
    }

}
