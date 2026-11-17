import type { Meta, StoryObj } from '@storybook/react';

import { Tabs, TabsContent, TabsList, TabsTrigger } from './tabs';

const meta: Meta = {
  title: 'Components/Tabs',
};

export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <Tabs defaultValue="month" className="w-[360px]">
      <TabsList>
        <TabsTrigger value="month">Месяц</TabsTrigger>
        <TabsTrigger value="week">Неделя</TabsTrigger>
        <TabsTrigger value="list">Список</TabsTrigger>
      </TabsList>
      <TabsContent value="month">Вид календаря: месяц</TabsContent>
      <TabsContent value="week">Вид календаря: неделя</TabsContent>
      <TabsContent value="list">Вид календаря: список</TabsContent>
    </Tabs>
  ),
};
