package forge.gui.layout;

/**
 * This interface requires a repaintThis() method, which
 * boost performance by locally repaints a component,
 * rather than repainting the entire screen.
 * 
 * <br><br><i>(I at beginning of class name denotes an interface.)</i>
 */
public interface ILocalRepaint {
    /** */
    void repaintThis();
}
