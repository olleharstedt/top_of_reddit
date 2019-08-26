<?php

namespace olleharstedt\top_of_reddit\misc;

function javaTrace($ex, $seen = null)
{
    $starter = $seen ? 'Caused by: ' : '';
    $result = array();
    if (!$seen) {
        $seen = array();
    }
    $trace  = $ex->getTrace();
    $prev   = $ex->getPrevious();
    $result[] = sprintf('%s%s: %s', $starter, get_class($ex), $ex->getMessage());
    $file = $ex->getFile();
    $line = $ex->getLine();
    while (true) {
        $current = "$file:$line";
        if (is_array($seen) && in_array($current, $seen)) {
            $result[] = sprintf(' ... %d more', count($trace)+1);
            break;
        }
        $result[] = sprintf(
            ' at %s%s%s(%s%s%s)',
            count($trace) && array_key_exists('class', $trace[0]) ? str_replace('\\', '.', $trace[0]['class']) : '',
            count($trace) && array_key_exists('class', $trace[0]) && array_key_exists('function', $trace[0]) ? '.' : '',
            count($trace) && array_key_exists('function', $trace[0]) ? str_replace('\\', '.', $trace[0]['function']) : '(main)',
            $line === null ? $file : basename($file),
            $line === null ? '' : ':',
            $line === null ? '' : $line
        );
        if (is_array($seen)) {
            $seen[] = "$file:$line";
        }
        if (!count($trace)) {
            break;
        }
        $file = array_key_exists('file', $trace[0]) ? $trace[0]['file'] : 'Unknown Source';
        $line = array_key_exists('file', $trace[0]) && array_key_exists('line', $trace[0]) && $trace[0]['line'] ? $trace[0]['line'] : null;
        array_shift($trace);
    }
    $result = join("\n", $result);
    if ($prev) {
        $result  .= "\n" . jTraceEx($prev, $seen);
    }

    return $result;
}
