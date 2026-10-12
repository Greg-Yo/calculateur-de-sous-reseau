package com.coco.subnet.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvalidInputExceptionTest {

    @Test
    void ofCreatesASingleError() {
        InvalidInputException ex = InvalidInputException.of(ErrorCode.EMPTY_INPUT, "cidr", "L'adresse est obligatoire");

        assertThat(ex.errors()).containsExactly(new InputError(ErrorCode.EMPTY_INPUT, "cidr", "L'adresse est obligatoire"));
        assertThat(ex.getMessage()).isEqualTo("L'adresse est obligatoire");
    }

    @Test
    void keepsAnImmutableCopyOfTheErrors() {
        List<InputError> errors = new ArrayList<>(List.of(
                new InputError(ErrorCode.EMPTY_NAME, "requirements[0].name", "premier"),
                new InputError(ErrorCode.HOSTS_NOT_POSITIVE, "requirements[0].hosts", "second")));

        InvalidInputException ex = new InvalidInputException(errors);
        errors.clear();

        assertThat(ex.errors()).hasSize(2);
        assertThat(ex.getMessage()).isEqualTo("premier");
        assertThatThrownBy(() -> ex.errors().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refusesAnEmptyOrNullList() {
        assertThatThrownBy(() -> new InvalidInputException(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvalidInputException(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
