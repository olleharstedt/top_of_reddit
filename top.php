<?php

/**
 * Async lib: https://github.com/spatie/async
 */

error_reporting(0);

require "vendor/autoload.php";

use PHPHtmlParser\Dom;
use Spatie\Async\Process;
use Spatie\Async\Pool;
use WebArticleExtractor\Extract;

$links = json_decode(file_get_contents(__DIR__ . '/links.json'), true);

$pool = Pool::create();

$period = $links['period'];

foreach ($links['links'] as $link) {
    $pool[] = async(function () use ($link) {
        $bakedLink = sprintf(
            'https://old.reddit.com/r/%s/top/?sort=top&t=week',
            $link['sub']
        );
        $dom = new Dom();
        $dom->loadFromUrl($bakedLink);
        $a = $dom->find('#siteTable .top-matter .title a');
        $votes = $dom->find('#siteTable .score.unvoted');
        $tabs = strlen($link['sub']) < 7 ? "\t\t" : "\t";

        $ext = Extract::extractFromURL($a->href);

        return '[' . $link['sub'] . "]" . $tabs
            . $votes->text . ":\t"
            . $a->text . PHP_EOL
            . "\t\t\t" . substr(wordwrap($ext->text, 60, PHP_EOL), 0, 400)
            . PHP_EOL
            . '----------------------------------------'
            . PHP_EOL;
    })->then(function ($result) {
        echo $result . PHP_EOL;
    })->catch(function (Throwable $ex) {
        echo $ex->getMessage() . PHP_EOL;
    });
}

await($pool);
