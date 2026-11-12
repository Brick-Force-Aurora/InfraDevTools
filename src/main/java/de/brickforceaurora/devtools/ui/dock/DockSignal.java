package de.brickforceaurora.devtools.ui.dock;

import me.lauriichan.snowframe.signal.ISignal;

public abstract class DockSignal implements ISignal {

    public static final class PreDock extends DockSignal {

        public static final PreDock INST = new PreDock();

        private PreDock() {}

    }

    public static final class PostDock extends DockSignal {

        public static final PostDock INST = new PostDock();

        private PostDock() {}

    }

}
