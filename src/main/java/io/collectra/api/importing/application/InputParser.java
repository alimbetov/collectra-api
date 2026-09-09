package io.collectra.api.importing.application;

import java.util.Collection;

interface InputParser {
    ParsedInput parse(byte[] content, Collection<String> sourcePaths, String recordPath);
}
