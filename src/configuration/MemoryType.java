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
 * <p>Classe Java de MemoryType complex type.
 * 
 * <p>O seguinte fragmento do esquema especifica o conteúdo esperado contido dentro desta classe.
 * 
 * <pre>
 * &lt;complexType name="MemoryType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="RomsDirectory" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="Rom48k" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="Rom128k0" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="Rom128k1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus20" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus21" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus2a0" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus2a1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus2a2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus2a3" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus30" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus31" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus32" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomPlus33" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomMF1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomMF128" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomMFPlus3" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="RomIF1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "MemoryType", propOrder = {
    "romsDirectory",
    "rom48K",
    "rom128K0",
    "rom128K1",
    "romPlus20",
    "romPlus21",
    "romPlus2A0",
    "romPlus2A1",
    "romPlus2A2",
    "romPlus2A3",
    "romPlus30",
    "romPlus31",
    "romPlus32",
    "romPlus33",
    "romMF1",
    "romMF128",
    "romMFPlus3",
    "romIF1"
})
public class MemoryType {

    @XmlElement(name = "RomsDirectory", required = true)
    protected String romsDirectory;
    @XmlElement(name = "Rom48k", required = true, defaultValue = "spectrum.rom")
    protected String rom48K;
    @XmlElement(name = "Rom128k0", required = true, defaultValue = "128-0.rom")
    protected String rom128K0;
    @XmlElement(name = "Rom128k1", required = true, defaultValue = "128-1.rom")
    protected String rom128K1;
    @XmlElement(name = "RomPlus20", required = true, defaultValue = "plus2-0.rom")
    protected String romPlus20;
    @XmlElement(name = "RomPlus21", required = true, defaultValue = "plus2-1.rom")
    protected String romPlus21;
    @XmlElement(name = "RomPlus2a0", required = true, defaultValue = "plus2a-0.rom")
    protected String romPlus2A0;
    @XmlElement(name = "RomPlus2a1", required = true, defaultValue = "plus2a-1.rom")
    protected String romPlus2A1;
    @XmlElement(name = "RomPlus2a2", required = true, defaultValue = "plus2a-2.rom")
    protected String romPlus2A2;
    @XmlElement(name = "RomPlus2a3", required = true, defaultValue = "plus2a-3.rom")
    protected String romPlus2A3;
    @XmlElement(name = "RomPlus30", required = true, defaultValue = "plus3-0.rom")
    protected String romPlus30;
    @XmlElement(name = "RomPlus31", required = true, defaultValue = "plus3-1.rom")
    protected String romPlus31;
    @XmlElement(name = "RomPlus32", required = true, defaultValue = "plus3-2.rom")
    protected String romPlus32;
    @XmlElement(name = "RomPlus33", required = true, defaultValue = "plus3-3.rom")
    protected String romPlus33;
    @XmlElement(name = "RomMF1", required = true, defaultValue = "mf1.rom")
    protected String romMF1;
    @XmlElement(name = "RomMF128", required = true, defaultValue = "mf128.rom")
    protected String romMF128;
    @XmlElement(name = "RomMFPlus3", required = true, defaultValue = "mfplus3.rom")
    protected String romMFPlus3;
    @XmlElement(name = "RomIF1", required = true, defaultValue = "if1.rom")
    protected String romIF1;

    /**
     * Obtém o valor da propriedade romsDirectory.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomsDirectory() {
        return romsDirectory;
    }

    /**
     * Define o valor da propriedade romsDirectory.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomsDirectory(String value) {
        this.romsDirectory = value;
    }

    /**
     * Obtém o valor da propriedade rom48K.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRom48K() {
        return rom48K;
    }

    /**
     * Define o valor da propriedade rom48K.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRom48K(String value) {
        this.rom48K = value;
    }

    /**
     * Obtém o valor da propriedade rom128K0.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRom128K0() {
        return rom128K0;
    }

    /**
     * Define o valor da propriedade rom128K0.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRom128K0(String value) {
        this.rom128K0 = value;
    }

    /**
     * Obtém o valor da propriedade rom128K1.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRom128K1() {
        return rom128K1;
    }

    /**
     * Define o valor da propriedade rom128K1.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRom128K1(String value) {
        this.rom128K1 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus20.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus20() {
        return romPlus20;
    }

    /**
     * Define o valor da propriedade romPlus20.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus20(String value) {
        this.romPlus20 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus21.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus21() {
        return romPlus21;
    }

    /**
     * Define o valor da propriedade romPlus21.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus21(String value) {
        this.romPlus21 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus2A0.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus2A0() {
        return romPlus2A0;
    }

    /**
     * Define o valor da propriedade romPlus2A0.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus2A0(String value) {
        this.romPlus2A0 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus2A1.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus2A1() {
        return romPlus2A1;
    }

    /**
     * Define o valor da propriedade romPlus2A1.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus2A1(String value) {
        this.romPlus2A1 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus2A2.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus2A2() {
        return romPlus2A2;
    }

    /**
     * Define o valor da propriedade romPlus2A2.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus2A2(String value) {
        this.romPlus2A2 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus2A3.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus2A3() {
        return romPlus2A3;
    }

    /**
     * Define o valor da propriedade romPlus2A3.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus2A3(String value) {
        this.romPlus2A3 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus30.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus30() {
        return romPlus30;
    }

    /**
     * Define o valor da propriedade romPlus30.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus30(String value) {
        this.romPlus30 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus31.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus31() {
        return romPlus31;
    }

    /**
     * Define o valor da propriedade romPlus31.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus31(String value) {
        this.romPlus31 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus32.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus32() {
        return romPlus32;
    }

    /**
     * Define o valor da propriedade romPlus32.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus32(String value) {
        this.romPlus32 = value;
    }

    /**
     * Obtém o valor da propriedade romPlus33.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomPlus33() {
        return romPlus33;
    }

    /**
     * Define o valor da propriedade romPlus33.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomPlus33(String value) {
        this.romPlus33 = value;
    }

    /**
     * Obtém o valor da propriedade romMF1.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomMF1() {
        return romMF1;
    }

    /**
     * Define o valor da propriedade romMF1.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomMF1(String value) {
        this.romMF1 = value;
    }

    /**
     * Obtém o valor da propriedade romMF128.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomMF128() {
        return romMF128;
    }

    /**
     * Define o valor da propriedade romMF128.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomMF128(String value) {
        this.romMF128 = value;
    }

    /**
     * Obtém o valor da propriedade romMFPlus3.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomMFPlus3() {
        return romMFPlus3;
    }

    /**
     * Define o valor da propriedade romMFPlus3.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomMFPlus3(String value) {
        this.romMFPlus3 = value;
    }

    /**
     * Obtém o valor da propriedade romIF1.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRomIF1() {
        return romIF1;
    }

    /**
     * Define o valor da propriedade romIF1.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRomIF1(String value) {
        this.romIF1 = value;
    }

}
