import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/**
 * Java conversion of COBOL program VSAMPRGM.
 *
 * File format:
 *   - 152-byte fixed-length input records
 *   - 152-byte fixed-length output records
 *   - PIC 99V99 values occupy four file bytes, for example 0520 = 5.20
 *   - No decimal point or record delimiter is written to the data file
 *
 * Usage:
 *   javac VsamPrgm.java
 *   java VsamPrgm [oemp.dat] [vsamoff.dat]
 *
 * Default behavior recreates vsamoff.dat on every run, preventing records from
 * an earlier test run from causing duplicate employee-ID errors.
 *
 * To reproduce COBOL OPEN EXTEND behavior explicitly:
 *   java VsamPrgm oemp.dat vsamoff.dat --append
 */
public final class VsamPrgm {
    private static final Charset FILE_CHARSET = StandardCharsets.ISO_8859_1;
    private static final int RECORD_LENGTH = 152;

    private static final int EMPID_OFFSET = 0;
    private static final int EMPID_LENGTH = 5;
    private static final int DESIGNATION_OFFSET = 5;
    private static final int DESIGNATION_LENGTH = 10;
    private static final int RATING_OFFSET = 15;
    private static final int RATING_LENGTH = 3;
    private static final int SALARY_OFFSET = 18;
    private static final int SALARY_LENGTH = 4;
    private static final int DOJ_OFFSET = 22;
    private static final int DOJ_LENGTH = 8;
    private static final int ATTENDANCE_OFFSET = 30;
    private static final int MONTHS_PER_YEAR = 12;
    private static final int DAYS_WORKED_LENGTH = 2;

    private static final BigDecimal WS_ALLOWANCES = new BigDecimal("90.10");

    private VsamPrgm() {
    }

    public static void main(String[] args) {
        Path input = Path.of(args.length > 0 ? args[0] : "oemp.dat");
        Path output = Path.of(args.length > 1 ? args[1] : "vsamoff.dat");
        boolean appendMode = args.length > 2 && "--append".equalsIgnoreCase(args[2]);

        String wsFs = Files.isRegularFile(input) ? "00" : "35";
        String wsFs1 = canOpenOutput(output) ? "00" : "30";
        System.out.println(" FILE STATUS FOR PS " + wsFs);
        System.out.println(" FILE STATUS FOR VSAM " + wsFs1);

        if (!"00".equals(wsFs) || !"00".equals(wsFs1)) {
            System.err.println("Unable to open input or output file.");
            System.exit(1);
        }

        try {
            Set<String> keys = appendMode ? loadExistingKeys(output) : new HashSet<>();
            StandardOpenOption[] options = appendMode
                    ? new StandardOpenOption[] {
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND
                    }
                    : new StandardOpenOption[] {
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    };

            try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(input));
                 BufferedOutputStream out = new BufferedOutputStream(
                         Files.newOutputStream(output, options))) {
                byte[] record;
                while ((record = readInputRecord(in)) != null) {
                    processRecord(record, out, keys);
                    System.out.println(" WS-EOF N");
                }
                System.out.println(" WS-EOF Y");
            }
        } catch (DuplicateKeyException e) {
            System.err.println(" FILE STATUS FOR VSAM 22");
            System.err.println(e.getMessage());
            System.exit(2);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("I/O or record-format error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean canOpenOutput(Path output) {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null && !Files.exists(parent)) {
                return false;
            }
            return !Files.exists(output)
                    || (Files.isRegularFile(output) && Files.isWritable(output));
        } catch (SecurityException e) {
            return false;
        }
    }

    private static Set<String> loadExistingKeys(Path output) throws IOException {
        Set<String> keys = new HashSet<>();
        if (!Files.exists(output) || Files.size(output) == 0) {
            return keys;
        }
        if (Files.size(output) % RECORD_LENGTH != 0) {
            throw new IOException("Existing output file is not composed of "
                    + RECORD_LENGTH + "-byte records: " + output);
        }
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(output))) {
            byte[] record;
            while ((record = readFixedRecord(in)) != null) {
                keys.add(field(record, EMPID_OFFSET, EMPID_LENGTH));
            }
        }
        return keys;
    }

    private static byte[] readInputRecord(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream record = new ByteArrayOutputStream(RECORD_LENGTH);
        while (record.size() < RECORD_LENGTH) {
            int value = in.read();
            if (value < 0) {
                if (record.size() == 0) {
                    return null;
                }
                throw new IOException("Short input record: expected " + RECORD_LENGTH
                        + " bytes, found " + record.size());
            }
            // Permit CR/LF between fixed records without treating it as record data.
            if (record.size() == 0 && (value == '\r' || value == '\n')) {
                continue;
            }
            record.write(value);
        }
        return record.toByteArray();
    }

    private static byte[] readFixedRecord(BufferedInputStream in) throws IOException {
        byte[] record = new byte[RECORD_LENGTH];
        int offset = 0;
        while (offset < RECORD_LENGTH) {
            int count = in.read(record, offset, RECORD_LENGTH - offset);
            if (count < 0) {
                if (offset == 0) {
                    return null;
                }
                throw new IOException("Short record in existing output file");
            }
            offset += count;
        }
        return record;
    }

    private static void processRecord(byte[] oempRecord,
                                      BufferedOutputStream output,
                                      Set<String> keys) throws IOException {
        String employeeId = field(oempRecord, EMPID_OFFSET, EMPID_LENGTH);
        String designation = field(oempRecord, DESIGNATION_OFFSET, DESIGNATION_LENGTH);
        String ratingText = field(oempRecord, RATING_OFFSET, RATING_LENGTH);
        String salaryText = field(oempRecord, SALARY_OFFSET, SALARY_LENGTH);
        String doj = field(oempRecord, DOJ_OFFSET, DOJ_LENGTH);

        // WS-EMP-OFF has its COBOL initial values before any MOVE occurs.
        System.out.println(" ".repeat(RECORD_LENGTH));
        System.out.println(" WS-EMPID " + " ".repeat(EMPID_LENGTH));
        System.out.println(" WS-DESIGNATION" + " ".repeat(DESIGNATION_LENGTH));
        System.out.println(" WS-RATING " + " ".repeat(RATING_LENGTH));
        System.out.println(" WS-SALARY 00.00");
        System.out.println(" WS-DOJ  " + " ".repeat(DOJ_LENGTH));

        BigDecimal wsSalary = parsePic99V99(salaryText, "WS-SALARY");
        System.out.println(" WS-SALARY " + formatDecimal(wsSalary));
        System.out.println("V-EMPID " + employeeId);
        System.out.println("V-DESIGNATION " + designation);
        System.out.println(" WS-RATING " + ratingText);

        char signCheck = ratingText.charAt(0);
        System.out.println(" O-RATING(1:1) " + signCheck);
        System.out.println(" WS-SIGNCHECK   " + signCheck);

        int wsTemp = parseDisplayInteger(ratingText, "O-RATING");
        System.out.println(" WS-TEMP " + String.format("%03d", Math.abs(wsTemp)));
        int vRating = signCheck == '-' ? -Math.abs(wsTemp) : wsTemp;
        System.out.println("V-RATING      " + formatSignedRating(vRating));

        BigDecimal vSalary = wsSalary.add(WS_ALLOWANCES)
                .setScale(2, RoundingMode.UNNECESSARY);
        validatePic99V99(vSalary, "V-SALARY");
        if (vSalary.signum() == 0) {
            System.out.println("SALARY SHOULD HAVE VALUE");
        } else {
            System.out.println("V-SALARY      " + formatDecimal(vSalary));
        }

        if (!isNumeric(designation)) {
            System.out.println("DESIGNATION IS NOT NUMERIC");
        }

        System.out.println("V-DOJ         " + doj);
        System.out.println(" V-EMPID " + employeeId);
        System.out.println(" V-DESIGNATION " + designation);
        System.out.println(" V-RATING " + formatSignedRating(vRating));
        System.out.println(" V-SALARY " + formatDecimal(vSalary));
        System.out.println(" V-DOJ   " + doj);
        System.out.println(" V-DAYS-WORKED 21");

        if (!keys.add(employeeId)) {
            throw new DuplicateKeyException(
                    "Duplicate V-EMPID in the current output data: " + employeeId);
        }

        /*
         * The COBOL statement is WRITE VSAMOFF-DETAILS FROM OEMP-REC.
         * FROM performs a group move from OEMP-REC immediately before WRITE.
         * Therefore the physical output remains the original 152-byte record.
         * Salary remains four bytes such as 0520, which represents 5.20.
         */
        output.write(oempRecord);
    }

    private static String field(byte[] record, int offset, int length) {
        return new String(record, offset, length, FILE_CHARSET);
    }

    private static int parseDisplayInteger(String text, String fieldName) {
        String value = text.trim();
        if (!value.matches("[+-]?\\d+")) {
            throw new IllegalArgumentException(fieldName + " is not numeric: '" + text + "'");
        }
        return Integer.parseInt(value);
    }

    private static BigDecimal parsePic99V99(String text, String fieldName) {
        if (!text.matches("\\d{4}")) {
            throw new IllegalArgumentException(
                    fieldName + " must contain four digits for PIC 99V99: '" + text + "'");
        }
        return new BigDecimal(text).movePointLeft(2)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void validatePic99V99(BigDecimal value, String fieldName) {
        BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.signum() < 0
                || normalized.compareTo(new BigDecimal("99.99")) > 0) {
            throw new IllegalArgumentException(
                    fieldName + " exceeds COBOL PIC 99V99: " + formatDecimal(normalized));
        }
    }

    private static String formatDecimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String formatSignedRating(int value) {
        if (Math.abs(value) > 99) {
            throw new IllegalArgumentException("V-RATING exceeds S9(02): " + value);
        }
        return (value < 0 ? "-" : "+") + String.format("%02d", Math.abs(value));
    }

    private static boolean isNumeric(String value) {
        return !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }

    private static final class DuplicateKeyException extends IOException {
        private DuplicateKeyException(String message) {
            super(message);
        }
    }
}
