$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$modRoot=Split-Path $PSScriptRoot -Parent
$resources=Join-Path $modRoot 'src/main/resources'
$assetRoot=Join-Path $resources 'assets/ludopathyroulette'
function Write-Json($relative,$value) {
    $target=Join-Path $resources $relative
    New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
    $value | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $target -Encoding utf8NoBOM
}

# ---------------------------------------------------------------- pixel art (16x16, escala vanilla)
function Hex($h,[int]$a=255){ $c=[System.Drawing.ColorTranslator]::FromHtml($h); [System.Drawing.Color]::FromArgb($a,$c.R,$c.G,$c.B) }
function Cl([double]$v){ [int][Math]::Max(0,[Math]::Min(255,[Math]::Round($v))) }
function Mul($c,[double]$f){ [System.Drawing.Color]::FromArgb($c.A,(Cl ($c.R*$f)),(Cl ($c.G*$f)),(Cl ($c.B*$f))) }
function Noise([int]$x,[int]$y,[int]$seed){ $n=(([long]$x*73856093) -bxor ([long]$y*19349663) -bxor ([long]$seed*83492791)) -band 0x7fffffff; ($n % 1000)/1000.0 }
function Px($b,$x,$y,$c){ if($x -ge 0 -and $y -ge 0 -and $x -lt $b.Width -and $y -lt $b.Height){$b.SetPixel($x,$y,$c)} }
function Rect($b,$x0,$y0,$x1,$y1,$c){ for($y=$y0;$y -le $y1;$y++){for($x=$x0;$x -le $x1;$x++){Px $b $x $y $c}} }
function Fill-Noise($b,$hex,$amp,$seed){ $base=Hex $hex; for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){ Px $b $x $y (Mul $base (1-$amp+2*$amp*(Noise $x $y $seed))) }} }
function Fill-Wood($b,$hex,$seed){
    $base=Hex $hex
    for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){
        $f=0.92+0.16*(Noise $x $y $seed)
        if($x%4 -eq 3){$f*=0.70} elseif($x%4 -eq 0){$f*=1.07}
        if((($y+($x -shr 2)*5)%7) -eq 0){$f*=0.9}
        Px $b $x $y (Mul $base $f)
    }}
}
function Tex($relative,[scriptblock]$paint,[int]$size=16){
    $target=Join-Path $assetRoot "textures/$relative.png"
    New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
    $bmp=[System.Drawing.Bitmap]::new($size,$size,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try { & $paint $bmp; $bmp.Save($target,[System.Drawing.Imaging.ImageFormat]::Png) } finally { $bmp.Dispose() }
}
$gold=Hex '#E3B64F'; $goldHi=Hex '#FFE08A'; $goldDk=Hex '#A8782A'; $goldDeep=Hex '#6B4A12'
$screen=Hex '#0E131A'

# Textura blanca: el renderizador de la rueda la tiñe con el color de cada categoría.
Tex 'block/white' { param($b) Rect $b 0 0 15 15 (Hex '#FFFFFF') }

# ---- Rueda de la Fortuna: caja carmesí de feria con marquesina dorada
Tex 'block/fortuna_side' { param($b)
    Fill-Noise $b '#9B2535' 0.05 11
    for($y=1;$y -lt 15;$y++){ Px $b 7 $y (Mul (Hex '#9B2535') 0.78); Px $b 8 $y (Mul (Hex '#9B2535') 1.12) }
    for($x=0;$x -lt 16;$x++){ Px $b $x 0 $gold; Px $b $x 1 $goldDk; Px $b $x 14 $goldDk; Px $b $x 15 $gold }
    Px $b 3 7 $goldHi; Px $b 3 8 $goldDk; Px $b 12 7 $goldHi; Px $b 12 8 $goldDk
    for($y=2;$y -lt 14;$y++){ Px $b 0 $y (Mul (Hex '#9B2535') 0.8); Px $b 15 $y (Mul (Hex '#9B2535') 0.8) }
}
Tex 'block/fortuna_top' { param($b)
    Fill-Wood $b '#5A3D2B' 21
    for($i=0;$i -lt 16;$i++){ Px $b $i 0 $gold; Px $b $i 15 $goldDk; Px $b 0 $i $gold; Px $b 15 $i $goldDk }
}
Tex 'block/fortuna_bottom' { param($b) Fill-Wood $b '#3E2A1D' 22 }
# Pantalla frontal: la rueda se dibuja encima (radio ~6 px) con la aguja arriba; marco con bombillas.
Tex 'block/fortuna_front' { param($b)
    for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){
        $d=[Math]::Sqrt(($x-7.5)*($x-7.5)+($y-7.5)*($y-7.5))
        Px $b $x $y (Mul $screen (1.25-0.04*$d+0.06*(Noise $x $y 31)))
    }}
    for($i=0;$i -lt 16;$i++){
        foreach($p in @(@($i,0),@($i,15),@(0,$i),@(15,$i))){ Px $b $p[0] $p[1] $goldDeep }
        $bulb=@(1,4,7,10,13) -contains $i
        $c=if($bulb){Hex '#FFF2B8'}else{$gold}
        if($i -ge 1 -and $i -le 14){ Px $b $i 1 $c; Px $b $i 14 $c; Px $b 1 $i $c; Px $b 14 $i $c }
    }
    foreach($p in @(@(1,1),@(14,1),@(1,14),@(14,14))){ Px $b $p[0] $p[1] $goldHi }
    for($i=2;$i -lt 14;$i++){ Px $b $i 2 (Hex '#1B222C'); Px $b 2 $i (Hex '#1B222C') }
}

# ---- Ruleta de Casino: mueble de nogal, fieltro verde y remates dorados
Tex 'block/bola_side' { param($b)
    Fill-Wood $b '#4A3122' 41
    for($x=0;$x -lt 16;$x++){ Px $b $x 0 $gold; Px $b $x 1 $goldDk; Px $b $x 14 $goldDk; Px $b $x 15 $gold
        Px $b $x 5 $goldDk; Px $b $x 10 $goldDk
        for($y=6;$y -le 9;$y++){ Px $b $x $y (Mul (Hex '#1F6B45') (0.94+0.12*(Noise $x $y 43))) } }
}
Tex 'block/bola_bottom' { param($b) Fill-Wood $b '#352316' 42 }
# Tapa: fieltro con cubeta (la rueda y la bola se dibujan encima, radio ~6 px).
Tex 'block/bola_top' { param($b)
    for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){
        $edge=[Math]::Min([Math]::Min($x,15-$x),[Math]::Min($y,15-$y))
        $d=[Math]::Sqrt(($x-7.5)*($x-7.5)+($y-7.5)*($y-7.5))
        if($edge -eq 0){ Px $b $x $y (Mul (Hex '#4A3122') (0.85+0.2*(Noise $x $y 44))) }
        elseif($edge -eq 1){ Px $b $x $y $gold }
        elseif($d -ge 6.1 -and $d -lt 7.3){ Px $b $x $y (Mul (Hex '#17482F') (0.95+0.1*(Noise $x $y 45))) }
        else { Px $b $x $y (Mul (Hex '#1E6B47') (0.93+0.14*(Noise $x $y 46))) }
    }}
}
Tex 'block/bola_front' { param($b)
    Fill-Wood $b '#4A3122' 47
    Rect $b 1 2 14 11 $goldDk
    Rect $b 2 3 13 10 $screen
    for($x=3;$x -le 12;$x++){
        $c=if($x -eq 3){Hex '#1E8A4C'}elseif($x%2 -eq 0){Hex '#B5303A'}else{Hex '#1C1C22'}
        Px $b $x 6 $c; Px $b $x 7 $c; Px $b $x 8 (Mul $c 0.75)
    }
    Px $b 8 4 (Hex '#FFFFFF'); Px $b 9 4 (Hex '#D8D2BC')
    Rect $b 5 13 10 14 $gold
    Rect $b 6 13 9 13 $goldHi
    for($x=0;$x -lt 16;$x++){ Px $b $x 0 $gold; Px $b $x 15 $goldDk }
}

# ---- Metal dorado para el marco (elementos extra de los modelos)
Tex 'block/trim' { param($b)
    for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){
        $f=0.95+0.1*(Noise $x $y 51)
        if($y -lt 2){$f*=1.18}elseif($y -gt 12){$f*=0.78}
        Px $b $x $y (Mul $gold $f)
    }}
    foreach($p in @(@(2,7),@(13,7))){ Px $b $p[0] $p[1] $goldHi; Px $b $p[0] ($p[1]+1) $goldDeep }
}

# ---- Cara de la rueda: eje vertical = radio (0 centro ... 15 borde), eje horizontal = anchura del sector.
# Filas 5-11 en gris (se tiñen con el color de la categoría); el resto es dorado fijo.
Tex 'block/wheel_face' { param($b)
    for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){
        $n=Noise $x $y 61
        if($y -le 2){ $c=Mul (Hex '#8A6420') (0.85+0.3*$n); if($y -eq 0 -and $x -ge 6 -and $x -le 9){$c=$goldHi} }
        elseif($y -le 4){ $c=Mul $gold (0.9+0.2*$n); if($y -eq 3){$c=Mul $c 1.15}else{$c=Mul $c 0.85} }
        elseif($y -le 11){
            $f=0.88+0.1*$n+0.015*($y-5)
            $c=Mul (Hex '#E8E8E8') $f
            if($x -le 0 -or $x -ge 15){ $c=Hex '#404040' }
            elseif($x -eq 1 -or $x -eq 14){ $c=Mul $c 0.8 }
            if($y -eq 8 -and $x -ge 6 -and $x -le 9){ $c=Hex '#FFFFFF' }
        }
        elseif($y -eq 12){ $c=Hex '#2A2118' }
        elseif($y -le 14){ $c=Mul $gold (0.92+0.16*$n); if($y -eq 13){$c=Mul $c 1.12} }
        else { $c=Hex '#6B4A12' }
        Px $b $x $y $c
    }}
}
# ---- Bola: perla con cuadrícula ecuatorial (proyección equirrectangular sobre la esfera)
Tex 'block/ball' { param($b)
    for($y=0;$y -lt 16;$y++){for($x=0;$x -lt 16;$x++){
        $f=0.93+0.07*(Noise $x $y 71)
        $lat=[Math]::Abs($y-7.5)/7.5
        $c=Mul (Hex '#F4F1E6') ($f-0.12*$lat*$lat)
        if($x -ge 3 -and $x -le 5 -and $y -ge 4 -and $y -le 6){ $c=Hex '#FFFFFF' }
        Px $b $x $y $c
    }}
}

# ---- Icono del mod: conservar el arte original al regenerar recursos.
& (Join-Path $PSScriptRoot 'update_icon.ps1')

# ---------------------------------------------------------------- modelos, estados, traducciones, datos
foreach($name in @('fortuna','bola')) {
    # models/block/$name.json se escribe a mano (marco dorado / bordes elevados); no se regenera aqui.
    $variants=@{}
    $orientations=@{north=0;east=90;south=180;west=270}
    foreach($facing in $orientations.Keys){$variants["facing=$facing"]=@{model="ludopathyroulette:block/$name";y=$orientations[$facing]}}
    Write-Json "assets/ludopathyroulette/blockstates/$name.json" @{variants=$variants}
    Write-Json "assets/ludopathyroulette/items/$name.json" @{model=@{type='minecraft:model';model="ludopathyroulette:block/$name"}}
}
Write-Json 'assets/ludopathyroulette/lang/es_es.json' @{'block.ludopathyroulette.fortuna'='Ruleta de la Fortuna';'block.ludopathyroulette.bola'='Ruleta de Casino';'death.attack.ludopathyroulette.ruleta'='%1$s tentó demasiado a la suerte';'subtitles.ludopathyroulette.click'='Clavija de ruleta';'subtitles.ludopathyroulette.win'='Gran premio de ruleta';'subtitles.ludopathyroulette.roll'='Bola rodando';'subtitles.ludopathyroulette.bounce'='Rebote de bola'}
Write-Json 'assets/ludopathyroulette/lang/en_us.json' @{'block.ludopathyroulette.fortuna'='Wheel of Fortune';'block.ludopathyroulette.bola'='Casino Roulette';'death.attack.ludopathyroulette.ruleta'='%1$s pushed their luck too far';'subtitles.ludopathyroulette.click'='Roulette peg clicks';'subtitles.ludopathyroulette.win'='Roulette jackpot';'subtitles.ludopathyroulette.roll'='Ball rolling';'subtitles.ludopathyroulette.bounce'='Ball bouncing'}
Write-Json 'assets/ludopathyroulette/sounds.json' @{click=@{subtitle='subtitles.ludopathyroulette.click';sounds=@(@{name='minecraft:block.note_block.hat';type='event'})};win=@{subtitle='subtitles.ludopathyroulette.win';sounds=@(@{name='minecraft:block.note_block.bell';type='event'})};roll=@{subtitle='subtitles.ludopathyroulette.roll';sounds=@(@{name='minecraft:block.grindstone.use';type='event'})};bounce=@{subtitle='subtitles.ludopathyroulette.bounce';sounds=@(@{name='minecraft:block.stone.hit';type='event'})}}
Write-Json 'data/ludopathyroulette/damage_type/ruleta.json' @{message_id='ludopathyroulette.ruleta';scaling='never';exhaustion=0.0}
Write-Json 'data/ludopathyroulette/recipe/fortuna.json' @{type='minecraft:crafting_shaped';category='redstone';pattern=@('PIP','RGR','PIP');key=@{P='minecraft:oak_planks';I='minecraft:iron_ingot';R='minecraft:redstone';G='minecraft:glass'};result=@{id='ludopathyroulette:fortuna';count=1}}
Write-Json 'data/ludopathyroulette/recipe/bola.json' @{type='minecraft:crafting_shaped';category='redstone';pattern=@('OGO','IRI','PPP');key=@{O='minecraft:gold_ingot';G='minecraft:glass';I='minecraft:iron_ingot';R='minecraft:redstone';P='minecraft:oak_planks'};result=@{id='ludopathyroulette:bola';count=1}}
# Imágenes de receta generadas desde los JSON definitivos, sin instalar o arrancar el juego.
$docs=Join-Path $modRoot 'docs/recipes'
New-Item -ItemType Directory -Force $docs | Out-Null
$names=@{'minecraft:gold_ingot'='Oro';'minecraft:iron_ingot'='Hierro';'minecraft:oak_planks'='Roble';'minecraft:redstone'='Redstone';'minecraft:glass'='Cristal'}
foreach($name in @('fortuna','bola')){
    $recipe=Get-Content -Raw (Join-Path $resources "data/ludopathyroulette/recipe/$name.json") | ConvertFrom-Json -AsHashtable
    $image=[System.Drawing.Bitmap]::new(640,260)
    $g=[System.Drawing.Graphics]::FromImage($image)
    $font=[System.Drawing.Font]::new('Segoe UI',13)
    $titleFont=[System.Drawing.Font]::new('Segoe UI',18,[System.Drawing.FontStyle]::Bold)
    try{
        $g.Clear([System.Drawing.ColorTranslator]::FromHtml('#20242C'))
        $g.DrawString("Ludopathy / $name",$titleFont,[System.Drawing.Brushes]::Wheat,16,12)
        for($i=0;$i -lt 9;$i++){
            $x=18+($i%3)*100;$y=55+[Math]::Floor($i/3)*62
            $g.FillRectangle([System.Drawing.Brushes]::DimGray,$x,$y,94,56)
            $item=$null
            if($recipe.ContainsKey('pattern')){$char=[string]$recipe.pattern[[Math]::Floor($i/3)][$i%3];if($recipe.key.ContainsKey($char)){$item=$recipe.key[$char]}}
            elseif($i -lt $recipe.ingredients.Count){$item=$recipe.ingredients[$i]}
            if($item){$g.DrawString($names[$item],$font,[System.Drawing.Brushes]::White,$x+6,$y+17)}
        }
        $g.DrawString('=>',$titleFont,[System.Drawing.Brushes]::Wheat,340,125)
        $g.DrawString("$($recipe.result.count) x $name",$titleFont,[System.Drawing.Brushes]::Wheat,400,125)
        $image.Save((Join-Path $docs "$name.png"),[System.Drawing.Imaging.ImageFormat]::Png)
    }finally{$g.Dispose();$image.Dispose();$font.Dispose();$titleFont.Dispose()}
}
Write-Output 'Texturas, icono, modelos, traducciones, sonidos, daño, recetas e imágenes generados.'
