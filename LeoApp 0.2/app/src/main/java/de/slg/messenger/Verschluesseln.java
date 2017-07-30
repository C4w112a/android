package de.slg.messenger;

abstract class Verschluesseln {
    static String encrypt(String text, String key) {
        StringBuilder builder = new StringBuilder();
        assert key.matches("[A-Z]*");
        for (int i = 0; i < text.length(); i++) {
            char textChar = text.charAt(i),
                    keyChar = key.charAt(i % key.length()),
                    encrypted = (char) (textChar + (keyChar - 65));
            if (isCapitalLetter(textChar)) {
                if (encrypted > 90)
                    encrypted -= 26;
                builder.append(encrypted);
            } else if (isLowerCaseLetter(textChar)) {
                if (encrypted > 122)
                    encrypted -= 26;
                builder.append(encrypted);
            } else {
                builder.append(textChar);
            }
        }
        return builder.toString();
    }

    static String encryptKey(String key) {
        String key2 = "ABCD";
        StringBuilder builder = new StringBuilder();
        assert key2.matches("[A-Z]*");
        for (int i = 0; i < key.length(); i++) {
            char textChar = key.charAt(i),
                    keyChar = key2.charAt(i % key2.length()),
                    encrypted = (char) (textChar + (keyChar - 65));
            if (encrypted > 90)
                encrypted -= 26;
            builder.append(encrypted);
        }
        return builder.toString();
    }

    static String decrypt(String text, String key) {
        StringBuilder builder = new StringBuilder();
        assert key.matches("[A-Z]*");
        for (int textIndex = 0; textIndex < text.length(); textIndex++) {
            char textChar = text.charAt(textIndex),
                    keyChar = key.charAt(textIndex % key.length()),
                    encrypted = (char) (textChar - (keyChar - 65));
            if (isCapitalLetter(textChar)) {
                if (encrypted < 65)
                    encrypted += 26;
                builder.append(encrypted);
            } else if (isLowerCaseLetter(textChar)) {
                if (encrypted < 97)
                    encrypted += 26;
                builder.append(encrypted);
            } else {
                builder.append(textChar);
            }
        }
        return builder.toString();
    }

    static String createKey(String text) {
        int length = text.length();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append((char) (65 + Math.random() * 26));
        }
        return builder.toString();
    }

    private static boolean isCapitalLetter(char c) {
        return c >= 65 && c <= 90;
    }

    private static boolean isLowerCaseLetter(char c) {
        return c >= 97 && c <= 122;
    }
}