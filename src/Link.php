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
}
