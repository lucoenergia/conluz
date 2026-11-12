package org.lucoenergia.conluz.domain.production.plant;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The CIL (Código de la Instalación de producción a efectos de Liquidación) of a plant: the Spanish settlement code
 * of a production installation, assigned by the distribution company, 25 ASCII alphanumeric characters (PLT-004).
 */
public final class PlantCil {

    public static final int LENGTH = 25;

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9]{" + LENGTH + "}");

    private PlantCil() {
    }

    /**
     * Upper-cases the given CIL and removes every whitespace and space character from it, anywhere in the value, so
     * that a code pasted from a distributor document compares equal to the stored one.
     *
     * @return the normalised CIL, or null when nothing is left, which means no CIL
     * @throws PlantCilInvalidException when what is left is not exactly {@value #LENGTH} ASCII alphanumeric characters
     */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder normalised = new StringBuilder(raw.length());
        raw.codePoints()
                .filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c))
                .forEach(normalised::appendCodePoint);
        if (normalised.isEmpty()) {
            return null;
        }
        // Checked before upper-casing, which could turn a non-ASCII letter into ASCII ones (ß into SS)
        if (!FORMAT.matcher(normalised).matches()) {
            throw new PlantCilInvalidException();
        }
        return normalised.toString().toUpperCase(Locale.ROOT);
    }
}
