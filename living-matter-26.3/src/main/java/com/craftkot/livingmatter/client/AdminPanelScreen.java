package com.craftkot.livingmatter.client;

import java.util.List;
import com.craftkot.livingmatter.AdminPanelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Actual Russian-language in-game administrator screen, not a mockup. */
public final class AdminPanelScreen extends Screen {
    private AdminPanelPacket snapshot;
    private int page;
    private String confirmation="";
    private int left,top,panelWidth,panelHeight;

    public AdminPanelScreen(AdminPanelPacket data) {
        super(Component.literal("Living Matter — Панель администратора"));
        this.snapshot=data;
    }

    public void accept(AdminPanelPacket value) {
        snapshot=value;
        page=Math.min(page,Math.max(0,(value.closest().size()-1)/10));
        confirmation="";
        rebuildWidgets();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }

    @Override protected void init() {
        panelWidth=Math.min(444,width-16);
        panelHeight=Math.min(314,height-14);
        left=(width-panelWidth)/2;
        top=(height-panelHeight)/2;
        int half=(panelWidth-24)/2;
        int x=left+10, right=left+panelWidth/2+1;
        int buttonWidth=(half-6)/2;
        int y=top+86;
        row(x,y,buttonWidth,
            snapshot.highlighting()?"Скрыть подсветку":"Показать заражение",
            "Обновить",
            ()->command("vision"),()->command("panel"));
        y+=26;
        row(x,y,buttonWidth,"Заразить блок","Очистить блок",
            ()->command("infect"),()->command("cure"));
        y+=26;
        row(x,y,buttonWidth,"Создать мозг","Удалить мозг",
            ()->command("brain"),()->command("unbrain"));
        y+=26;
        row(x,y,buttonWidth,"Пробудить блок","Живая листва",
            ()->command("awaken"),()->command("leaf"));
        y+=26;
        row(x,y,buttonWidth,"Хтонический моб","Тест превращения",
            ()->confirm("chthon","chthontest"),
            ()->confirm("chthontest","chthontest"));
        y+=26;
        row(x,y,buttonWidth,"Ходячая масса","Очистить район",
            ()->command("walktest"),()->confirm("purgearea","purgearea"));

        int navY=top+panelHeight-38;
        addRenderableWidget(Button.builder(Component.literal("◀"),
            b->{page=Math.max(0,page-1);rebuildWidgets();})
            .bounds(right+6,navY,43,19).build());
        addRenderableWidget(Button.builder(Component.literal("▶"),
            b->{page=Math.min(Math.max(0,(snapshot.closest().size()-1)/10),page+1);
                rebuildWidgets();})
            .bounds(right+55,navY,43,19).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"),
            b->onClose()).bounds(left+panelWidth-86,top+panelHeight-20,77,17).build());
    }

    private void row(int x,int y,int bw,String first,String second,
                     Runnable actionA,Runnable actionB){
        addRenderableWidget(Button.builder(Component.literal(first),b->actionA.run())
            .bounds(x,y,bw,21).build());
        addRenderableWidget(Button.builder(Component.literal(second),b->actionB.run())
            .bounds(x+bw+5,y,bw,21).build());
    }

    private void command(String subcommand) {
        var mc=Minecraft.getInstance();
        if(mc.getConnection()==null)return;
        mc.getConnection().sendCommand("livingmatter "+subcommand);
        if(!subcommand.equals("panel")) mc.getConnection().sendCommand("livingmatter panel");
    }

    private void confirm(String operation,String label) {
        if(confirmation.equals(operation)){
            confirmation="";
            command(operation);
        } else {
            confirmation=operation;
            rebuildWidgets();
        }
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        super.extractBackground(g,mouseX,mouseY,delta);
        g.fill(left,top,left+panelWidth,top+panelHeight,0xF20D1117);
        g.fill(left,top,left+panelWidth,top+3,0xFF9B3945);
        g.outline(left,top,panelWidth,panelHeight,0xFF52616A);
        g.fill(left+7,top+30,left+panelWidth-7,top+78,0xFF18212A);
        g.fill(left+panelWidth/2,top+84,left+panelWidth/2+1,top+panelHeight-42,0xFF46505B);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        super.extractRenderState(g,mouseX,mouseY,delta);
        int white=0xFFE7EDF2,muted=0xFF9EADB6,red=0xFFE98B95;
        g.text(font,"LIVING MATTER  /  ПАНЕЛЬ АДМИНИСТРАТОРА",left+12,top+12,white);
        g.text(font,"Заражено: "+snapshot.totalInfected()+"   Рядом: "+snapshot.nearbyInfected()
            +"   Мозгов: "+snapshot.totalBrains(),left+14,top+37,white);
        g.text(font,"Мозгов рядом: "+snapshot.nearbyBrains()
            +"   Монстров рядом: "+snapshot.nearbyMonsters(),left+14,top+52,muted);
        g.text(font,"Управление миром",left+12,top+74,red);
        int right=left+panelWidth/2+9;
        g.text(font,"Заражённые блоки (24 блока):",right,top+88,red);
        List<BlockPos> positions=snapshot.closest();
        int from=Math.min(page*10,positions.size()),until=Math.min(from+10,positions.size());
        if(positions.isEmpty()){
            g.text(font,"Поблизости заражения нет",right,top+109,muted);
        } else {
            for(int i=from;i<until;i++) {
                BlockPos pos=positions.get(i);
                String line=(i+1)+".  X: "+pos.getX()+"  Y: "+pos.getY()+"  Z: "+pos.getZ();
                g.text(font,line,right,top+108+(i-from)*13,
                    i%2==0?white:muted);
            }
        }
        g.text(font,"Стр. "+(page+1)+" / "+Math.max(1,(positions.size()+9)/10),
            right+102,top+panelHeight-33,muted);
        String warning=confirmation.equals("purgearea")
            ?"ВНИМАНИЕ: удалить заражение в радиусе 12. Нажми ещё раз!"
            : confirmation.equals("chthontest")
            ?"ВНИМАНИЕ: блоки исчезнут навсегда. Нажми ещё раз!"
            :confirmation.equals("chthon")
            ?"ВНИМАНИЕ: монстр поглотит блоки. Нажми ещё раз!"
            :"Наведи прицел на блок до открытия панели. Кнопка F8 — открыть снова.";
        g.text(font,warning,left+11,top+panelHeight-52,
            confirmation.isEmpty()?muted:red);
    }
}
