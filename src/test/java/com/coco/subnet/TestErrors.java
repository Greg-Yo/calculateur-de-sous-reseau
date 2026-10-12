package com.coco.subnet;

import com.coco.subnet.model.InputError;
import com.coco.subnet.model.InvalidInputException;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exécute un appel qui doit échouer et renvoie les erreurs portées par l'exception. */
public final class TestErrors {

    private TestErrors() {
    }

    public static List<InputError> errors(Executable call) {
        return assertThrows(InvalidInputException.class, call).errors();
    }

    public static InputError firstError(Executable call) {
        return errors(call).get(0);
    }
}
