<?php

namespace olleharstedt\top_of_reddit;

class Link
{
    /**
     * @var string
     */
    public $sub;

    /**
     */
    public function __construct($link)
    {
        $this->sub = $link['sub'];
    }

    /**
     * @param string $period
     * @return string
     */
    public function getBakedLink(string $period)
    {
        return sprintf(
            'https://old.reddit.com/r/%s/top/?sort=top&t=%s',
            $this->sub,
            $period
        );
    }

    /**
     * @return Link[]
     */
    public static function getLinksFromJson(string $file)
    {
        $links = [];
        $json = json_decode(file_get_contents($file), true);
        foreach ($json['links'] as $item) {
            $links[] = new Link($item);
        }
        return $links;
    }
}
