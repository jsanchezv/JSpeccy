package snapshots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotFactoryTest {

    @Test
    @DisplayName("The snapshot factory returns a snapshot loader when an valid snapshot file is passed as a parameter")
    void testSnapshotFactory_returnsSnapshotLoader_whenValidSnapshotFileIsPassedAsParameter() {

        final File file = new File("alien.sna");
        SnapshotFile snapshot = SnapshotFactory.getSnapshot(file);
        assertNotNull(snapshot, String.format("The snapshot loader for file '%s' is valid", file.getName()));
    }

    @Test
    @DisplayName("The snapshot factory throws an IllegalArgumentException when an unknown snapshot file extension is encountered")
    void testSnapshotFactory_throwsIllegalArgumentException_whenAnUnknownSnapshotFileExtensionIsEncountered() {

        final File file = new File("alien.zip");
        assertThrows(IllegalArgumentException.class, () -> SnapshotFactory.getSnapshot(file));
    }

    @Test
    @DisplayName("The snapshot factory throws a NullPointerException when a null file is passed as a parameter")
    void testSnapshotFactory_throwsNullPointerException_whenNullFileIsPassedAsParameter() {

        final File nullSnapshot = null;
        assertThrows(NullPointerException.class, () -> SnapshotFactory.getSnapshot(nullSnapshot));
    }

}
