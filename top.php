<?php

/**
 * Async lib: https://github.com/spatie/async
 */

require "vendor/autoload.php";

use PHPHtmlParser\Dom;
use Spatie\Async\Process;
use Spatie\Async\Pool;
use WebArticleExtractor\Extract;

$links = [
    [
        'sub' => 'php'
    ],
    [
        'sub' => 'programming'
    ],
    [
        'sub' => 'worldnews'
    ],
    [
        'sub' => 'news'
    ],
    [
        'sub' => 'compsci'
    ]
];

$pool = Pool::create();

foreach ($links as $link) {
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

        //echo wordwrap($result->text, 60, PHP_EOL);die;

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
        var_dump($ex->getMessage());
    });
}

await($pool);
