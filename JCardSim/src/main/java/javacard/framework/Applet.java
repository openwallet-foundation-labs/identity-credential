package javacard.framework;

import com.licel.jcardsim.base.SimulatorSystem;
import com.licel.jcardsim.utils.BiConsumer;

public abstract class Applet {
  /**
   * The current registration callback, set by SimulatorRuntime via reflection.
   */
  public static final ThreadLocal<BiConsumer<Applet,AID>> registrationCallback
      = new ThreadLocal<BiConsumer<Applet,AID>>();

  /**
   * To create an instance of the <code>Applet</code> subclass, the Java Card runtime environment
   * will call this static method first.
   * <p>The applet should
   * perform any necessary initializations and must call one of the <code>register()</code> methods.
   * Only one Applet instance can be successfully registered from within this install.
   * The installation is considered successful when the call to <code>register()</code>
   * completes without an exception. The installation is deemed unsuccessful if the
   * <code>install</code> method does not call a
   * <code>register()</code> method, or if an exception is thrown from within
   * the <code>install</code> method prior to the call to a <code>register()</code>
   * method, or if every call to the <code>register()</code> method results in an exception.
   * If the installation is unsuccessful, the Java Card runtime environment must perform all the necessary clean up
   * when it receives control.
   * Successful installation makes the applet instance capable of being selected via a
   * SELECT APDU command.<p>
   * Installation parameters are supplied in the byte array parameter and
   * must be in a format using length-value (LV) pairs as defined below:
   * <pre>
   * bArray[0] = length(Li) of instance AID, bArray[1..Li] = instance AID bytes,
   * bArray[Li+1]= length(Lc) of control info, bArray[Li+2..Li+Lc+1] = control info,
   * bArray[Li+Lc+2] = length(La) of applet data, bArray[Li+Lc+2..Li+Lc+La+1] = applet data
   * </pre>
   * In the above format, any of the lengths: Li, Lc or La may be zero. The control
   * information is implementation dependent.
   * <p>
   * The <code>bArray</code> object is a global array. If the applet
   * desires to preserve any of this data, it should copy
   * the data into its own object.
   * <p><code>bArray</code> is zeroed by the Java Card runtime environment after the return from the
   * <code>install()</code> method.<p>
   * References to the <code>bArray</code> object
   * cannot be stored in class variables or instance variables or array components.
   * See <em>Runtime Environment Specification for the Java Card Platform</em>, section 6.2.2 for details.<p>
   * The implementation of this method provided by
   * <code>Applet</code> class throws an <code>ISOException</code> with
   * reason code = <code>ISO7816.SW_FUNC_NOT_SUPPORTED</code>.
   * <p>Note:<ul>
   * <li><em>Exceptions thrown by this method after successful installation are caught
   * by the Java Card runtime environment and processed by the Installer.</em>
   * </ul>
   * @param bArray the array containing installation parameters
   * @param bOffset the starting offset in bArray
   * @param bLength the length in bytes of the parameter data in bArray
   * The maximum value of bLength is 127.
   * @throws ISOException if the install method failed
   */
  public static void install(byte bArray[], short bOffset, byte bLength)
      throws ISOException {
    ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
  }


  /**
   * Called by the Java Card runtime environment to inform this applet that it has been selected when
   * no applet from the same package is active on any other logical channel.
   * <p>It is called when a SELECT APDU command or MANAGE CHANNEL OPEN APDU
   * command is received and before the applet is selected.
   * SELECT APDU commands use instance AID bytes for applet selection.
   * See <em>Runtime Environment Specification for the Java Card Platform</em>, section 4.5 for details.<p>
   * A subclass of <code>Applet</code> should override this method
   * if it should perform any initialization that may be required to
   * process APDU commands that may follow.
   * This method returns a boolean to indicate that it is ready to accept incoming APDU
   * commands via its <code>process()</code> method. If this method returns false, it indicates to
   * the Java Card runtime environment that this Applet declines to be selected.
   * <p>Note:<ul>
   * <li><em>The <CODE>javacard.framework.MultiSelectable.select(</CODE>) method is not
   * called if this method is invoked.</em>
   * </ul>
   * <p>
   * The implementation of this method provided by
   * <code>Applet</code> class returns <code>true</code>.<p>
   * @return <code>true</code> to indicate success, <code>false</code> otherwise
   */
  public boolean select() {
    return true;
  }

  /**
   * Called by the Java Card runtime environment to inform that this currently selected applet is
   * being deselected on this logical channel and no applet from the same package
   * is still active on any other logical channel.
   * After deselection, this logical channel will be closed or another applet
   * (or the same applet) will be selected on this logical channel.
   * It is called when a SELECT APDU command or a MANAGE CHANNEL CLOSE APDU
   * command is received by the Java Card runtime environment. This method is invoked prior to another
   * applet's or this very applet's <code>select()</code> method being invoked.
   * <p>
   * A subclass of <code>Applet</code> should override this method if
   * it has any cleanup or bookkeeping work to be performed before another
   * applet is selected.
   * <p>
   * The default implementation of this method provided by <code>Applet</code> class does nothing.<p>
   * Notes:<ul>
   * <li><em>The <CODE>javacard.framework.MultiSelectable.deselect(</CODE>) method is not
   * called if this method is invoked.</em>
   * <li><em>Unchecked exceptions thrown by this method are caught by the Java Card runtime environment but the
   * applet is deselected.</em>
   * <li><em>Transient objects of </em><code>JCSystem.CLEAR_ON_DESELECT</code><em> clear event type
   * are cleared to their default value by the Java Card runtime environment after this method.</em>
   * <li><em>This method is NOT called on reset or power loss.</em>
   * </ul>
   */
  public void deselect() {
  }

  /**
   * Called by the Java Card runtime environment to obtain a shareable interface object from this server applet, on
   * behalf of a request from a client applet. This method executes in the applet context of
   * <code>this</code> applet instance.
   * The client applet initiated this request by calling the
   * <code>JCSystem.getAppletShareableInterfaceObject()</code> method.
   * See <em>Runtime Environment Specification for the Java Card Platform</em>, section 6.2.4 for details.
   * <p>Note:<ul>
   * <li><em>The </em><code>clientAID</code><em> parameter is a Java Card runtime environment-owned </em><code>AID</code><em>
   * instance. Java Card runtime environment-owned instances of <code>AID</code> are permanent Java Card runtime environment
   * Entry Point Objects and can be accessed from any applet context.
   * References to these permanent objects can be stored and re-used.</em>
   * </ul>
   * @param clientAID the <code>AID</code> object of the client applet
   * @param parameter optional parameter byte. The parameter byte may be used by the client to specify
   * which shareable interface object is being requested.
   * @return the shareable interface object or <code>null</code>
   */
  public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
    return null;
  }

  /**
   * This method is used by the applet to register <code>this</code> applet instance with
   * the Java Card runtime environment and to
   * assign the Java Card platform name of the applet as its instance AID bytes.
   * One of the <code>register()</code> methods must be called from within <code>install()</code>
   * to be registered with the Java Card runtime environment.
   * See <em>Runtime Environment Specification for the Java Card Platform</em>, section 3.1 for details.
   * <p>Note:<ul>
   * <li><em>The phrase "Java Card platform name of the applet" is a reference to the </em><code>AID[AID_length]</code><em>
   * item in the </em><code>applets[]</code><em> item of the </em><code>applet_component</code><em>, as documented in Section 6.5
   * Applet Component in the Virtual Machine Specification for the Java Card Platform.</em>
   * </ul>
   * @throws SystemException with the following reason codes:<ul>
   * <li><code>SystemException.ILLEGAL_AID</code> if the <code>Applet</code> subclass AID bytes are in use or
   * if the applet instance has previously successfully registered with the Java Card runtime environment via one of the
   * <code>register()</code> methods or if a Java Card runtime environment initiated <code>install()</code> method execution is not in progress.
   * </ul>
   */
  protected final void register()
      throws SystemException {
    BiConsumer<Applet,AID> callback = registrationCallback.get();
    if (callback == null) { // not called from install()
      SystemException.throwIt(SystemException.ILLEGAL_AID);
    }
    else {
      callback.accept(this, null);
    }
  }

  /**
   * This method is used by the applet to register <code>this</code> applet instance with the Java Card runtime environment and
   * assign the specified AID bytes as its instance AID bytes.
   * One of the <code>register()</code> methods must be called from within <code>install()</code>
   * to be registered with the Java Card runtime environment.
   * See <em>Runtime Environment Specification for the Java Card Platform</em>, section 3.1 for details.
   * <p>Note:<ul>
   * <li><em>The implementation may require that the instance AID bytes specified are the same as that
   * supplied in the install parameter data. An ILLEGAL_AID exception may be thrown otherwise.</em>
   * </ul>
   * @param bArray the byte array containing the AID bytes
   * @param bOffset the start of AID bytes in bArray
   * @param bLength the length of the AID bytes in bArray
   * @throws SystemException with the following reason code:<ul>
   *<li><code>SystemException.ILLEGAL_VALUE</code> if the <code>bLength</code> parameter is
   *less than <code>5</code> or greater than <code>16</code>.
   *<li><code>SystemException.ILLEGAL_AID</code> if the specified instance AID bytes are in use or
   *if the applet instance has previously successfully registered with the Java Card runtime environment via one of the
   *<code>register()</code> methods or if a Java Card runtime environment-initiated <code>install()</code> method execution is not in progress.
   *</ul>
   */
  protected final void register(byte bArray[], short bOffset, byte bLength)
      throws SystemException {
    if (bLength < 5 || bLength > 16) {
      throw new SystemException(SystemException.ILLEGAL_VALUE);
    }
    BiConsumer<Applet,AID> callback = registrationCallback.get();
    if (callback == null) { // not called from install()
      throw new SystemException(SystemException.ILLEGAL_AID);
    }
    callback.accept(this, new AID(bArray, bOffset, bLength));
  }

  /**
   * This method is used by the applet <code>process()</code> method to distinguish
   * the SELECT APDU command which selected <code>this</code> applet, from all other
   * other SELECT APDU commands which may relate to file or internal applet state selection.
   * @return <code>true</code> if <code>this</code> applet is being selected
   */
  protected final boolean selectingApplet() {
    return SimulatorSystem.instance().isAppletSelecting(this);
  }

  protected Applet() {
  }


  protected static boolean reSelectingApplet() {
    return false;
  }
  public abstract void process(APDU var1) throws ISOException;

}
