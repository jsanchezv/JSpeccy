//
// Este arquivo foi gerado pela Arquitetura JavaTM para Implementa��o de Refer�ncia (JAXB) de Bind XML, v2.3.0 
// Consulte <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Todas as modifica��es neste arquivo ser�o perdidas ap�s a recompila��o do esquema de origem. 
// Gerado em: 2019.11.17 �s 01:44:49 PM BRT 
//


package configuration;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java de EmulatorSettingsType complex type.
 * 
 * <p>O seguinte fragmento do esquema especifica o conte�do esperado contido dentro desta classe.
 * 
 * <pre>
 * &lt;complexType name="EmulatorSettingsType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="confirmActions" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="autosaveConfigOnExit" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EmulatorSettingsType", propOrder = {
    "confirmActions",
    "autosaveConfigOnExit"
})
public class EmulatorSettingsType {

    @XmlElement(defaultValue = "true")
    protected boolean confirmActions;
    @XmlElement(defaultValue = "false")
    protected boolean autosaveConfigOnExit;

    /**
     * Obt�m o valor da propriedade confirmActions.
     * 
     */
    public boolean isConfirmActions() {
        return confirmActions;
    }

    /**
     * Define o valor da propriedade confirmActions.
     * 
     */
    public void setConfirmActions(boolean value) {
        this.confirmActions = value;
    }

    /**
     * Obt�m o valor da propriedade autosaveConfigOnExit.
     * 
     */
    public boolean isAutosaveConfigOnExit() {
        return autosaveConfigOnExit;
    }

    /**
     * Define o valor da propriedade autosaveConfigOnExit.
     * 
     */
    public void setAutosaveConfigOnExit(boolean value) {
        this.autosaveConfigOnExit = value;
    }

}
