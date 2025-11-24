package org.whispercat.sidemenu;

/**
 *
 * @author Raven
 */
public class MenuAction {

    public boolean isCancel() {
        return cancel;
    }

    public void cancel() {
        this.cancel = true;
    }

    private boolean cancel = false;
}
