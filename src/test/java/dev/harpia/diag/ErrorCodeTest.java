package dev.harpia.diag;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A diagnostic code is a promise: the same code always means the same refusal, so a reader can look
 * it up. Two constants sharing one code break that promise silently — nothing fails, the code just
 * stops identifying anything.
 */
class ErrorCodeTest {

    @Test
    void noTwoRefusalsShareACode() throws IllegalAccessException {
        Map<String, List<String>> byCode = new HashMap<>();
        for (Field field : ErrorCodes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            byCode.computeIfAbsent((String) field.get(null), code -> new ArrayList<>())
                    .add(field.getName());
        }

        assertThat(byCode)
                .allSatisfy((code, names) -> assertThat(names)
                        .as("%s means %s", code, names)
                        .hasSize(1));
    }

    @Test
    void everyCodeIsInTheRangeItsFamilyOwns() throws IllegalAccessException {
        for (Field field : ErrorCodes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            assertThat((String) field.get(null))
                    .as("%s carries a code outside the HRP<family><number> shape", field.getName())
                    .matches("HRP[1-7][0-9]{3}");
        }
    }
}
