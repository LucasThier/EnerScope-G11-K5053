package org.enerscope.user.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates cryptographically strong, human-distributable passwords.
 *
 * <p>Every generated password has a fixed length and is guaranteed to contain
 * at least one lower-case letter, one upper-case letter, one digit and one
 * symbol, so it satisfies common complexity policies. Randomness comes from
 * {@link SecureRandom}; ambiguous characters (O/0, l/1, etc.) are excluded to
 * make the credentials easier to copy by hand.</p>
 */
@Component
public class PasswordGenerator {

    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%*?-_";
    private static final String ALL = LOWER + UPPER + DIGITS + SYMBOLS;

    /** Default length; comfortably above the 8-char minimum enforced on registration. */
    public static final int DEFAULT_LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public String generate(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Password length must be at least 8");
        }

        List<Character> chars = new ArrayList<>(length);
        // Guarantee one character from each class first.
        chars.add(pick(LOWER));
        chars.add(pick(UPPER));
        chars.add(pick(DIGITS));
        chars.add(pick(SYMBOLS));
        // Fill the rest from the full alphabet.
        for (int i = chars.size(); i < length; i++) {
            chars.add(pick(ALL));
        }
        // Shuffle so the guaranteed characters are not always at the front.
        Collections.shuffle(chars, random);

        StringBuilder sb = new StringBuilder(length);
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
