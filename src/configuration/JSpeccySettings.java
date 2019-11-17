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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java de anonymous complex type.
 * 
 * <p>O seguinte fragmento do esquema especifica o conteúdo esperado contido dentro desta classe.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="SpectrumSettings" type="{http://xml.netbeans.org/schema/JSpeccy}SpectrumType"/&gt;
 *         &lt;element name="MemorySettings" type="{http://xml.netbeans.org/schema/JSpeccy}MemoryType"/&gt;
 *         &lt;element name="TapeSettings" type="{http://xml.netbeans.org/schema/JSpeccy}TapeSettingsType"/&gt;
 *         &lt;element name="KeyboardJoystickSettings" type="{http://xml.netbeans.org/schema/JSpeccy}KeyboardJoystickType"/&gt;
 *         &lt;element name="AY8912Settings" type="{http://xml.netbeans.org/schema/JSpeccy}AY8912Type"/&gt;
 *         &lt;element name="RecentFilesSettings" type="{http://xml.netbeans.org/schema/JSpeccy}RecentFilesType"/&gt;
 *         &lt;element name="Interface1Settings" type="{http://xml.netbeans.org/schema/JSpeccy}Interface1Type"/&gt;
 *         &lt;element name="EmulatorSettings" type="{http://xml.netbeans.org/schema/JSpeccy}EmulatorSettingsType"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "spectrumSettings",
    "memorySettings",
    "tapeSettings",
    "keyboardJoystickSettings",
    "ay8912Settings",
    "recentFilesSettings",
    "interface1Settings",
    "emulatorSettings"
})
@XmlRootElement(name = "JSpeccySettings")
public class JSpeccySettings {

    @XmlElement(name = "SpectrumSettings", required = true)
    protected SpectrumType spectrumSettings;
    @XmlElement(name = "MemorySettings", required = true)
    protected MemoryType memorySettings;
    @XmlElement(name = "TapeSettings", required = true)
    protected TapeSettingsType tapeSettings;
    @XmlElement(name = "KeyboardJoystickSettings", required = true)
    protected KeyboardJoystickType keyboardJoystickSettings;
    @XmlElement(name = "AY8912Settings", required = true)
    protected AY8912Type ay8912Settings;
    @XmlElement(name = "RecentFilesSettings", required = true)
    protected RecentFilesType recentFilesSettings;
    @XmlElement(name = "Interface1Settings", required = true)
    protected Interface1Type interface1Settings;
    @XmlElement(name = "EmulatorSettings", required = true)
    protected EmulatorSettingsType emulatorSettings;

    /**
     * Obtém o valor da propriedade spectrumSettings.
     * 
     * @return
     *     possible object is
     *     {@link SpectrumType }
     *     
     */
    public SpectrumType getSpectrumSettings() {
        return spectrumSettings;
    }

    /**
     * Define o valor da propriedade spectrumSettings.
     * 
     * @param value
     *     allowed object is
     *     {@link SpectrumType }
     *     
     */
    public void setSpectrumSettings(SpectrumType value) {
        this.spectrumSettings = value;
    }

    /**
     * Obtém o valor da propriedade memorySettings.
     * 
     * @return
     *     possible object is
     *     {@link MemoryType }
     *     
     */
    public MemoryType getMemorySettings() {
        return memorySettings;
    }

    /**
     * Define o valor da propriedade memorySettings.
     * 
     * @param value
     *     allowed object is
     *     {@link MemoryType }
     *     
     */
    public void setMemorySettings(MemoryType value) {
        this.memorySettings = value;
    }

    /**
     * Obtém o valor da propriedade tapeSettings.
     * 
     * @return
     *     possible object is
     *     {@link TapeSettingsType }
     *     
     */
    public TapeSettingsType getTapeSettings() {
        return tapeSettings;
    }

    /**
     * Define o valor da propriedade tapeSettings.
     * 
     * @param value
     *     allowed object is
     *     {@link TapeSettingsType }
     *     
     */
    public void setTapeSettings(TapeSettingsType value) {
        this.tapeSettings = value;
    }

    /**
     * Obtém o valor da propriedade keyboardJoystickSettings.
     * 
     * @return
     *     possible object is
     *     {@link KeyboardJoystickType }
     *     
     */
    public KeyboardJoystickType getKeyboardJoystickSettings() {
        return keyboardJoystickSettings;
    }

    /**
     * Define o valor da propriedade keyboardJoystickSettings.
     * 
     * @param value
     *     allowed object is
     *     {@link KeyboardJoystickType }
     *     
     */
    public void setKeyboardJoystickSettings(KeyboardJoystickType value) {
        this.keyboardJoystickSettings = value;
    }

    /**
     * Obtém o valor da propriedade ay8912Settings.
     * 
     * @return
     *     possible object is
     *     {@link AY8912Type }
     *     
     */
    public AY8912Type getAY8912Settings() {
        return ay8912Settings;
    }

    /**
     * Define o valor da propriedade ay8912Settings.
     * 
     * @param value
     *     allowed object is
     *     {@link AY8912Type }
     *     
     */
    public void setAY8912Settings(AY8912Type value) {
        this.ay8912Settings = value;
    }

    /**
     * Obtém o valor da propriedade recentFilesSettings.
     * 
     * @return
     *     possible object is
     *     {@link RecentFilesType }
     *     
     */
    public RecentFilesType getRecentFilesSettings() {
        return recentFilesSettings;
    }

    /**
     * Define o valor da propriedade recentFilesSettings.
     * 
     * @param value
     *     allowed object is
     *     {@link RecentFilesType }
     *     
     */
    public void setRecentFilesSettings(RecentFilesType value) {
        this.recentFilesSettings = value;
    }

    /**
     * Obtém o valor da propriedade interface1Settings.
     * 
     * @return
     *     possible object is
     *     {@link Interface1Type }
     *     
     */
    public Interface1Type getInterface1Settings() {
        return interface1Settings;
    }

    /**
     * Define o valor da propriedade interface1Settings.
     * 
     * @param value
     *     allowed object is
     *     {@link Interface1Type }
     *     
     */
    public void setInterface1Settings(Interface1Type value) {
        this.interface1Settings = value;
    }

    /**
     * Obtém o valor da propriedade emulatorSettings.
     * 
     * @return
     *     possible object is
     *     {@link EmulatorSettingsType }
     *     
     */
    public EmulatorSettingsType getEmulatorSettings() {
        return emulatorSettings;
    }

    /**
     * Define o valor da propriedade emulatorSettings.
     * 
     * @param value
     *     allowed object is
     *     {@link EmulatorSettingsType }
     *     
     */
    public void setEmulatorSettings(EmulatorSettingsType value) {
        this.emulatorSettings = value;
    }

}
