<?php

/**
 * Async lib: https://github.com/spatie/async
 * PDF: https://mpdf.github.io/
 */

namespace olleharstedt\top_of_reddit;

error_reporting(0);

require "vendor/autoload.php";
require "src/Link.php";
require "src/Misc.php";

use PHPHtmlParser\Dom;
use Spatie\Async\Process;
use Spatie\Async\Pool;
use WebArticleExtractor\Extract;
use Mpdf\Mpdf;
use olleharstedt\top_of_reddit\misc;

$links = Link::getLinksFromJson(__DIR__ . '/links.json');

$pool = Pool::create();

$period = "day";

//$ext = Extract::extractFromURL('https://www.theguardian.com/world/2019/aug/25/trump-officials-voice-anger-at-g7-focus-on-niche-issues-such-as-climate-change');
//echo ($ext->text);
//die;

//echo json_encode(mime_content_type(''));
//$dom = new Dom();
//$dom->loadFromUrl('https://old.reddit.com/r/vim/comments/cv8c5y/vim_marks/');
//$a = $dom->find('p.title a');
//echo $a->href;
//echo json_encode(exif_imagetype($a->href));
//$img = file_get_contents($a->href);
//$bytes = file_put_contents('img.png', $img);
//echo $bytes;
//$mpdf = new \Mpdf\Mpdf();
//$mpdf->WriteHTML('<img src="img.png" />');
//$mpdf->Output();
//return;

$opt = getopt('p:');
if ($opt) {
    $period = $opt['p'];
    if (!in_array($period, ['day', 'week', 'month', 'year'])) {
        die('Unknown period: ' . $period . PHP_EOL);
    }
}

$output = '';

foreach ($links as $link) {
    try {
        $bakedLink = $link->getBakedLink($period);

        $dom = new Dom();
        $dom->loadFromUrl($bakedLink);
        $a = $dom->find('#siteTable .top-matter .title a');
        $votes = $dom->find('#siteTable .score.unvoted');
        $tabs = strlen($link->sub) < 7 ? "\t\t" : "\t";

        // Check for image
        $dom = new Dom();
        if ($a->href[0] === '/') {
            $href = 'https://old.reddit.com'  . $a->href;
        } else {
            $href = $a->href;
        }
        fwrite(STDERR, 'Have url ' . $href . PHP_EOL);
        $dom->loadFromUrl($href);
        $a2 = $dom->find('p.title a');

        if ($a2 && $a2->href && @exif_imagetype($a2->href) === 3) {
            fwrite(STDERR, 'Checking for image at ' . $a2->href . PHP_EOL);
            $output .= 'image';
            $img = file_get_contents($a2->href);
            $imageFile = basename($a2->href);
            $bytes = file_put_contents($imageFile, $img);
            if ($bytes === 0) {
                $output .= 'Did not write any img data';
            } else {
                $output .= sprintf(
                    '<img src="%s" />',
                    $imageFile
                );
            }
        } else {
            fwrite(STDERR, 'Extracting from ' . $href . PHP_EOL);
            $ext = Extract::extractFromURL($href);
            if (empty($ext)) {
                $output .= 'could not extract article';
            }
            $output .= '<h2>' . $link->sub . "</h2>" . $tabs
                . $votes->text . ":\t"
                . $a->text . '<br/>'
                . nl2br(wordwrap($ext->text, 60, PHP_EOL))
                . '<br/>'
                . '<hr>'
                . '<br/>';
        }
    } catch (Throwable $ex) {
        $output .= javaTrace($ex);
    }
}

//var_dump(getopt(null, ['pdf']));die;

echo $output;

$mpdf = new \Mpdf\Mpdf();
$mpdf->WriteHTML($output);
$mpdf->Output();
