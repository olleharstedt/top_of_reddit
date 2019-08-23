<?php

/**
 * Async lib: https://github.com/spatie/async
 */

require "vendor/autoload.php";

use PHPHtmlParser\Dom;
use Spatie\Async\Process;
use Spatie\Async\Pool;
use WebArticleExtractor\Extract;

$result = Extract::extractFromURL(
    'https://www.bbc.com/news/world-latin-america-49452789'
);

//print_r($result);
echo wordwrap($result->text, 60, PHP_EOL);die;

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
        return '[' . $link['sub'] . "]" . $tabs
            . $votes->text . ":\t"
            . $a->text;
    })->then(function ($result) {
        echo $result . PHP_EOL;
    })->catch(function (Throwable $ex) {
        var_dump($ex->getMessage());
    });
}

await($pool);
