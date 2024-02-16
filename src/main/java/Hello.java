import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.Display;

public class Hello extends MIDlet {
    public Hello() {
        Display display = Display.getDisplay(this);
        TextBox hello = new TextBox("Hello!", "Hello World!", 256, 0);
        display.setCurrent(hello);
    }
    public void startApp() {}
    public void destroyApp(boolean unconditional) {}
    public void pauseApp() {}
}