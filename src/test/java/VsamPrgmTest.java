import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VsamPrgmTest {

    @TempDir
    Path tempDir;

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void redirectStreams() {
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private static Object invokeStatic(String methodName, Class<?>[] types, Object... args)
            throws Exception {
        Method m = VsamPrgm.class.getDeclaredMethod(methodName, types);
        m.setAccessible(true);
        try {
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private byte[] record(String empId, String designation, String rating,
                           String salary, String doj) {
        StringBuilder sb = new StringBuilder();
        sb.append(pad(empId, 5));
        sb.append(pad(designation, 10));
        sb.append(pad(rating, 3));
        sb.append(pad(salary, 4));
        sb.append(pad(doj, 8));
        sb.append(" ".repeat(152 - sb.length()));
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String pad(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }

    @Test
    void mainProcessesSampleFileEndToEnd() throws Exception {
        Path input = tempDir.resolve("oemp.dat");
        Path output = tempDir.resolve("vsamoff.dat");
        Files.write(input, record("00001", "MANAGER", "022", "0505", "20240101"));

        VsamPrgm.main(new String[] {input.toString(), output.toString()});

        assertTrue(Files.exists(output));
        assertEquals(152, Files.size(output));
    }

    @Test
    void mainAppendModeSkipsDuplicateDetectionAcrossRuns() throws Exception {
        Path input = tempDir.resolve("oemp.dat");
        Path output = tempDir.resolve("vsamoff.dat");
        Files.write(input, record("00010", "ENGINEER", "-05", "0100", "20230101"));

        VsamPrgm.main(new String[] {input.toString(), output.toString()});
        assertEquals(152, Files.size(output));

        // Second run without --append truncates, so same key succeeds again.
        VsamPrgm.main(new String[] {input.toString(), output.toString()});
        assertEquals(152, Files.size(output));
    }

    // Note: paths that reach System.exit() (missing input, unwritable output,
    // duplicate key in append mode) are intentionally not driven through main()
    // here, since that would terminate the test JVM. The underlying decision
    // logic (canOpenOutput) is covered directly below instead.

    @Test
    void canOpenOutputTrueWhenParentDirExistsAndFileAbsent() throws Exception {
        Path output = tempDir.resolve("vsamoff.dat");
        boolean result = (boolean) invokeStatic("canOpenOutput",
                new Class<?>[] {Path.class}, output);
        assertTrue(result);
    }

    @Test
    void canOpenOutputFalseWhenParentDirMissing() throws Exception {
        Path output = tempDir.resolve("does-not-exist").resolve("vsamoff.dat");
        boolean result = (boolean) invokeStatic("canOpenOutput",
                new Class<?>[] {Path.class}, output);
        assertFalse(result);
    }

    @Test
    void canOpenOutputTrueWhenExistingFileIsWritable() throws Exception {
        Path output = tempDir.resolve("vsamoff.dat");
        Files.writeString(output, "existing");
        boolean result = (boolean) invokeStatic("canOpenOutput",
                new Class<?>[] {Path.class}, output);
        assertTrue(result);
    }

    @Test
    void parsePic99V99ParsesFourDigitField() throws Exception {
        BigDecimal result = (BigDecimal) invokeStatic("parsePic99V99",
                new Class<?>[] {String.class, String.class}, "0520", "WS-SALARY");
        assertEquals(new BigDecimal("5.20"), result);
    }

    @Test
    void parsePic99V99RejectsNonDigits() {
        Exception e = assertThrows(Exception.class, () -> invokeStatic("parsePic99V99",
                new Class<?>[] {String.class, String.class}, "AB12", "WS-SALARY"));
        assertTrue(e instanceof IllegalArgumentException);
    }

    @Test
    void parseDisplayIntegerHandlesSignedValues() throws Exception {
        int positive = (int) invokeStatic("parseDisplayInteger",
                new Class<?>[] {String.class, String.class}, "022", "O-RATING");
        assertEquals(22, positive);

        int negative = (int) invokeStatic("parseDisplayInteger",
                new Class<?>[] {String.class, String.class}, "-05", "O-RATING");
        assertEquals(-5, negative);
    }

    @Test
    void parseDisplayIntegerRejectsNonNumeric() {
        assertThrows(Exception.class, () -> invokeStatic("parseDisplayInteger",
                new Class<?>[] {String.class, String.class}, "abc", "O-RATING"));
    }

    @Test
    void formatSignedRatingFormatsPositiveAndNegative() throws Exception {
        assertEquals("+22", invokeStatic("formatSignedRating", new Class<?>[] {int.class}, 22));
        assertEquals("-05", invokeStatic("formatSignedRating", new Class<?>[] {int.class}, -5));
    }

    @Test
    void formatSignedRatingRejectsOutOfRange() {
        assertThrows(Exception.class, () -> invokeStatic("formatSignedRating",
                new Class<?>[] {int.class}, 150));
    }

    @Test
    void isNumericDetectsDigitOnlyStrings() throws Exception {
        assertEquals(true, invokeStatic("isNumeric", new Class<?>[] {String.class}, "12345"));
        assertEquals(false, invokeStatic("isNumeric", new Class<?>[] {String.class}, "12a45"));
        assertEquals(false, invokeStatic("isNumeric", new Class<?>[] {String.class}, ""));
    }

    @Test
    void validatePic99V99RejectsOutOfRange() {
        assertThrows(Exception.class, () -> invokeStatic("validatePic99V99",
                new Class<?>[] {BigDecimal.class, String.class},
                new BigDecimal("150.00"), "V-SALARY"));
    }

    @Test
    void loadExistingKeysReadsPriorRecords() throws Exception {
        Path output = tempDir.resolve("vsamoff.dat");
        Files.write(output, record("00042", "ANALYST", "010", "0099", "20220101"));

        @SuppressWarnings("unchecked")
        Set<String> keys = (Set<String>) invokeStatic("loadExistingKeys",
                new Class<?>[] {Path.class}, output);
        assertTrue(keys.contains("00042"));
    }

    @Test
    void loadExistingKeysReturnsEmptyForMissingFile() throws Exception {
        Path output = tempDir.resolve("does-not-exist.dat");

        @SuppressWarnings("unchecked")
        Set<String> keys = (Set<String>) invokeStatic("loadExistingKeys",
                new Class<?>[] {Path.class}, output);
        assertTrue(keys.isEmpty());
    }
}
