package com.github.viktor235.schedalertbot.compare;

import lombok.extern.slf4j.Slf4j;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.PropertyChange;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class CompareService {

    public List<FieldDiff> compare(Object o1, Object o2) {
        Diff diff = JaversBuilder.javers().build()
                .compare(o1, o2);
        log.debug(diff.prettyPrint());
        // Covers value and container (list, set, map) changes; for containers left/right are the whole old/new values.
        // Nested object fields (ReferenceChange) are not expected: compared models hold only values and lists,
        // and their left/right would be Javers ids rather than readable values.
        return diff.getChanges().stream()
                .filter(PropertyChange.class::isInstance)
                .map(PropertyChange.class::cast)
                .map(pc -> new FieldDiff(
                        pc.getPropertyName(),
                        pc.getLeft(),
                        pc.getRight()
                ))
                .toList();
    }
}
