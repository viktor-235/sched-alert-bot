package com.github.viktor235.schedalertbot.compare;

import lombok.extern.slf4j.Slf4j;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.ValueChange;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class CompareService {

    public List<FieldDiff> compare(Object o1, Object o2) {
        Diff diff = JaversBuilder.javers().build()
                .compare(o1, o2);
        log.debug(diff.prettyPrint());
        return diff.getChanges().stream()
                .filter(ValueChange.class::isInstance)
                .map(ValueChange.class::cast)
                .map(vc -> new FieldDiff(
                        vc.getPropertyName(),
                        vc.getLeft(),
                        vc.getRight()
                ))
                .toList();
    }
}
